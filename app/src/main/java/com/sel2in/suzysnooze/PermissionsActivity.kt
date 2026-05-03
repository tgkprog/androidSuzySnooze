package com.sel2in.suzysnooze

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sel2in.suzysnooze.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.permissions_title)

        setupPermissionButtons()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.secondary_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_main -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupPermissionButtons() {
        binding.btnGrantNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }

        binding.btnGrantDnd.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }

        binding.btnGrantExactAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        binding.btnGrantBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        binding.btnGrantWriteSettings.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        binding.btnGrantDeviceAdmin.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_device_admin))
                .setMessage(getString(R.string.device_admin_description))
                .setPositiveButton("Continue") { _, _ ->
                    val componentName = ComponentName(this, SuzyDeviceAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                            getString(R.string.device_admin_description))
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnGrantAccessibility.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_accessibility))
                .setMessage(getString(R.string.accessibility_service_description))
                .setPositiveButton("Continue") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnGrantShowOnTop.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        binding.btnDismissPermissions.setOnClickListener {
            finish()
        }
    }

    private fun updatePermissionStatus() {
        // Notifications
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        binding.txtNotificationsStatus.text = if (notificationsGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // DND Access
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.isNotificationPolicyAccessGranted
        } else {
            true
        }
        binding.txtDndStatus.text = if (dndGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Exact Alarm
        val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        binding.txtExactAlarmStatus.text = if (exactAlarmGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Battery Optimization
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
        binding.txtBatteryStatus.text = if (batteryGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Write Settings
        val writeSettingsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(this)
        } else {
            true
        }
        binding.txtWriteSettingsStatus.text = if (writeSettingsGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Device Admin
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, SuzyDeviceAdminReceiver::class.java)
        val deviceAdminGranted = dpm.isAdminActive(componentName)
        binding.txtDeviceAdminStatus.text = if (deviceAdminGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Accessibility Service
        val accessibilityGranted = isAccessibilityServiceEnabled()
        binding.txtAccessibilityStatus.text = if (accessibilityGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }

        // Display Over Other Apps
        val showOnTopGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        binding.txtShowOnTopStatus.text = if (showOnTopGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val colonSplitter = ":"
        val serviceName = "$packageName/${SuzyAccessibilityService::class.java.name}"
        
        return enabledServices?.split(colonSplitter)?.any { it.equals(serviceName, ignoreCase = true) } == true
    }
}
