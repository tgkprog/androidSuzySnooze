package com.sel2in.suzysnooze

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sel2in.suzysnooze.databinding.ActivityMainBinding
import java.util.ArrayDeque
import java.util.Date
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_notification_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: SnoozeRepository

    private val pendingPanelIntents: ArrayDeque<Intent> = ArrayDeque()
    private var pendingSnoozeConfig: PendingSnoozeConfig? = null
    private var waitingForPanelResult: Boolean = false

    // Use main-looper Handler (not view.postDelayed) so minimize is not tied to view lifecycle.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SnoozeRepository.KEY_ACTIVE_PUBLIC) {
            runOnUiThread { updateStatus() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SnoozeRepository(this)
        
        FileLogger.log(this, TAG, "=== APP STARTED ===")
        FileLogger.log(this, TAG, "Log file: ${FileLogger.getLogFilePath(this)}")

        setupQuickButtons()
        setupPickers()
        setupPermissionShortcuts()
        setupMenuButtons()

        binding.btnStartCustom.setOnClickListener { startCustomSnooze() }
        binding.btnCancelSnooze.setOnClickListener { cancelSnooze() }

        displayBuildInfo()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        repository.registerListener(prefListener)
        updateBatteryButtonVisibility()
        if (waitingForPanelResult) {
            waitingForPanelResult = false
            startPendingSnoozeIfReady()
        }
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        repository.unregisterListener(prefListener)
    }

    private fun setupQuickButtons() {
        val quickButtons = listOf(
            binding.btn1 to 1L,
            binding.btn2 to 2L,
            binding.btn5 to 5L,
            binding.btn20 to 20L,
            binding.btn30 to 30L
        )

        quickButtons.forEach { (button, minutes) ->
            button.setOnClickListener { startSnooze(minutes) }
        }
    }

    private fun setupPickers() {
        binding.hourPicker.minValue = 0
        binding.hourPicker.maxValue = 9999
        binding.minutePicker.minValue = 0
        binding.minutePicker.maxValue = 59
        binding.minutePicker.value = 5
    }

    private fun setupPermissionShortcuts() {
        binding.btnWifiQuick.setOnClickListener {
            openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        binding.btnAirplaneQuick.setOnClickListener {
            openIntent(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        }

        binding.btnInternetQuick.setOnClickListener {
            openIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        }

        binding.btnBatteryQuick.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        binding.btnNotificationPermission.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnDndPermission.setOnClickListener {
            openDndPermissionScreen()
        }

        binding.btnWriteSettings.setOnClickListener {
            openWriteSettingsScreen()
        }
        
        binding.btnExactAlarm.setOnClickListener {
            openExactAlarmSettings()
        }
    }

    private fun setupMenuButtons() {
        binding.btnWebsite.setOnClickListener {
            openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL)))
        }

        binding.btnDonate.setOnClickListener {
            openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://sel2in.com/news/hot/pay")))
        }

        binding.btnAds.setOnClickListener {
            openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://sel2in.com/news/hot/")))
        }
    }

    private fun startCustomSnooze() {
        val hours = binding.hourPicker.value
        val minutes = binding.minutePicker.value
        val totalMinutes = hours * 60L + minutes
        startSnooze(totalMinutes)
    }

    private fun startSnooze(minutes: Long) {
        FileLogger.log(this, TAG, "=== START SNOOZE REQUESTED: $minutes minutes ===")
        
        if (minutes <= 0L) {
            Toast.makeText(this, getString(R.string.toast_duration_required), Toast.LENGTH_SHORT)
                .show()
            return
        }

        val wantsMute = binding.chkMute.isChecked
        val wantsVibration = binding.chkVibration.isChecked

        if (!wantsMute && !wantsVibration) {
            Toast.makeText(this, getString(R.string.toast_no_action_selected), Toast.LENGTH_SHORT).show()
            return
        }

        // Check DND permission (CRITICAL for preventing vibration)
        if (!isDndAccessGranted()) {
            FileLogger.log(this, TAG, "ERROR: No DND permission - cannot prevent vibration!")
            Toast.makeText(this, "Grant Do Not Disturb access to prevent vibration", Toast.LENGTH_LONG).show()
            openDndPermissionScreen()
            return
        }

        // For short snoozes, check notification permission (needed for exact alarms)
        if (minutes < 15) {
            if (!areNotificationsGranted()) {
                FileLogger.log(this, TAG, "ERROR: No notification permission for short snooze")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(this, getString(R.string.toast_notification_required_short), Toast.LENGTH_LONG).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Toast.makeText(this, getString(R.string.toast_notification_required_short), Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        FileLogger.log(this, TAG, "All permission checks passed, starting snooze")
        cancelExistingSnooze()

        pendingSnoozeConfig = PendingSnoozeConfig(
            minutes = minutes,
            wantsMute = wantsMute,
            wantsVibration = wantsVibration
        )

        startPendingSnoozeIfReady()
    }

    private fun updateBatteryButtonVisibility() {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            binding.btnBatteryQuick.visibility = View.GONE
        } else {
            binding.btnBatteryQuick.visibility = View.VISIBLE
        }
    }

    private fun startPendingSnoozeIfReady() {
        val config = pendingSnoozeConfig ?: return
        pendingSnoozeConfig = null
        pendingPanelIntents.clear()

        FileLogger.log(this, TAG, "Applying snooze: ${config.minutes} min, mute=${config.wantsMute}, vibOff=${config.wantsVibration}")

        try {
            // Apply SilentController
            if (config.wantsMute || config.wantsVibration) {
                SilentController.applySilent(this)
            } else if (SilentController.hasSavedState(this)) {
                SilentController.restoreState(this)
            }

            val durationMillis = TimeUnit.MINUTES.toMillis(config.minutes)
            val endTimestamp = System.currentTimeMillis() + durationMillis
            val isShortSnooze = config.minutes < 15
            
            repository.saveSnooze(
                endTimestamp,
                config.wantsMute,
                config.wantsVibration,
                isShortSnooze
            )
            
            // Choose exact alarm vs WorkManager
            if (isShortSnooze) {
                FileLogger.log(this, TAG, "SHORT snooze - using exact alarm")
                RestoreWorker.scheduleExactAlarm(this, durationMillis)
            } else {
                FileLogger.log(this, TAG, "LONG snooze - using WorkManager")
                RestoreWorker.schedule(this, durationMillis)
            }

            Toast.makeText(
                this,
                getString(R.string.toast_snooze_started, formatDuration(config.minutes)),
                Toast.LENGTH_SHORT
            ).show()

            val formattedTime = DateFormat.getTimeFormat(this).format(Date(endTimestamp))
            NotificationHelper.showOngoingSnoozeNotification(this, formattedTime)

            updateStatus()
            hideAppAfterDelay()
            
            FileLogger.log(this, TAG, "Snooze started successfully until $formattedTime")
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Failed to start snooze", e)
        }
    }

    private fun cancelExistingSnooze() {
        if (!repository.isSnoozeActive()) return
        
        FileLogger.log(this, TAG, "Cancelling existing snooze")
        
        try {
            RestoreWorker.cancelExactAlarm(this)
            RestoreWorker.cancel(this)
            SilentController.restoreState(this)
            repository.clearSnooze()
            NotificationHelper.cancelOngoingSnoozeNotification(this)
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Failed to cancel existing snooze", e)
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        val uri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri)
        openIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openAppNotificationSettings()
            return
        }
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            openAppNotificationSettings()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        openIntent(intent)
    }

    private fun isDndAccessGranted(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun openDndPermissionScreen() {
        openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun openWriteSettingsScreen() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        openIntent(intent)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            openIntent(intent)
        }
    }

    private fun areNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, ex.localizedMessage ?: ex.message ?: "", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun cancelSnooze(showToast: Boolean = true) {
        if (!repository.isSnoozeActive()) {
            if (showToast) {
                Toast.makeText(this, getString(R.string.toast_no_active_snooze), Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        FileLogger.log(this, TAG, "=== USER CANCELLED SNOOZE ===")

        try {
            RestoreWorker.cancelExactAlarm(this)
            RestoreWorker.cancel(this)
            pendingSnoozeConfig = null
            pendingPanelIntents.clear()
            waitingForPanelResult = false
            SilentController.restoreState(this)
            repository.clearSnooze()
            NotificationHelper.cancelOngoingSnoozeNotification(this)
            updateStatus()
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Cancel failed", e)
        }

        if (showToast) {
            Toast.makeText(this, getString(R.string.toast_snooze_canceled), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun updateStatus() {
        if (repository.isSnoozeActive()) {
            val time = Date(repository.snoozeEndTime())
            val formatted = DateFormat.getTimeFormat(this).format(time)
            val remaining = repository.remainingMinutes()
            binding.txtSnoozeStatus.text = getString(R.string.status_running_with_time, formatted, remaining)
            binding.btnCancelSnooze.visibility = View.VISIBLE
        } else {
            binding.txtSnoozeStatus.setText(R.string.status_idle)
            binding.btnCancelSnooze.visibility = View.GONE
        }
    }

    private fun displayBuildInfo() {
        binding.txtVersion.text = "Version: ${BuildConfig.BUILD_VERSION}"
        binding.txtBuildDate.text = "Build Date: ${BuildConfig.BUILD_DATE}"
    }

    private fun hideAppAfterDelay() {
        // Cancel any previously scheduled hide so double-tapping snooze doesn't stack runnables.
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { moveTaskToBack(true) }
        hideRunnable = r
        // 350 ms gives the Toast time to appear while keeping the delay imperceptible.
        // mainHandler is NOT tied to the view lifecycle, so it always fires even when
        // the activity is returning from a Settings panel during onResume.
        mainHandler.postDelayed(r, 350)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val WEB_URL = "https://sel2in.com/snooze/"

        /**
         * Format minutes into a human-readable duration string.
         * < 60 min      → "X minutes"
         * < 24 hours    → "H hr Y min"
         * >= 24 hours   → "D days H hr Y min"
         */
        fun formatDuration(totalMinutes: Long): String {
            if (totalMinutes < 60) return "$totalMinutes minutes"
            val days = totalMinutes / (24 * 60)
            val hours = (totalMinutes % (24 * 60)) / 60
            val mins = totalMinutes % 60
            return buildString {
                if (days > 0) append("$days day${if (days > 1) "s" else ""} ")
                if (hours > 0) append("$hours hr ")
                if (mins > 0) append("$mins min")
            }.trim()
        }
    }

    private data class PendingSnoozeConfig(
        val minutes: Long,
        val wantsMute: Boolean,
        val wantsVibration: Boolean
    )
}
