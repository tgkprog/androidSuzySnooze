package com.sel2in.suzysnooze

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sel2in.suzysnooze.databinding.ActivityRestorePopupBinding
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.PI

class RestorePopupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestorePopupBinding
    private lateinit var repository: SnoozeRepository
    private var audioTrack: AudioTrack? = null

    companion object {
        private const val TAG = "RestorePopupActivity"

        fun show(context: Context) {
            val intent = Intent(context, RestorePopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set flags like reachme does - simpler approach
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        binding = ActivityRestorePopupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set window to 80% of screen size (half seemed too small)
        setupWindowSize()
        
        repository = SnoozeRepository(this)

        FileLogger.log(this, TAG, "=== RESTORE POPUP SHOWN ===")
        
        // Bring MainActivity to foreground in PIP mode
        bringMainActivityToPipMode()

        setupButtons()
        playBeeps()
    }
    
    private fun bringMainActivityToPipMode() {
        try {
            // Launch MainActivity if not running
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainIntent)
            
            FileLogger.log(this, TAG, "MainActivity launched")
            
            // Send broadcast to enter PIP mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pipIntent = Intent(MainActivity.ACTION_ENTER_PIP).apply {
                    `package` = packageName
                }
                sendBroadcast(pipIntent)
                FileLogger.log(this, TAG, "PIP broadcast sent to MainActivity")
            }
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Failed to bring MainActivity to PIP", e)
        }
    }
    
    private fun setupWindowSize() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            displayMetrics.widthPixels = bounds.width()
            displayMetrics.heightPixels = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(displayMetrics)
        }
        
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val params = window.attributes
        
        // Set to 80% of screen for better visibility
        if (screenHeight > screenWidth) {
            // Portrait: 90% width, 70% height  
            params.width = (screenWidth * 0.9).toInt()
            params.height = (screenHeight * 0.7).toInt()
        } else {
            // Landscape: 70% width, 90% height
            params.width = (screenWidth * 0.7).toInt()
            params.height = (screenHeight * 0.9).toInt()
        }
        
        params.gravity = Gravity.CENTER
        window.attributes = params
        
        FileLogger.log(this, TAG, "Window size set: width=${params.width}, height=${params.height}")
    }
    
    private fun playBeeps() {
        Thread {
            try {
                val sampleRate = 44100
                
                // First beep: 1800 Hz for 600 ms
                playTone(1800.0, 600, sampleRate)
                
                // 100 ms break
                Thread.sleep(100)
                
                // Second beep: 2200 Hz for 750 ms
                playTone(2200.0, 750, sampleRate)
                
                FileLogger.log(this, TAG, "Beeps played successfully")
            } catch (e: Exception) {
                FileLogger.log(this, TAG, "Failed to play beeps", e)
            }
        }.start()
    }
    
    private fun playTone(frequencyHz: Double, durationMs: Int, sampleRate: Int) {
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        
        // Generate sine wave
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i / (sampleRate / frequencyHz)
            buffer[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        
        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.play()
        
        // Wait for playback to finish
        Thread.sleep(durationMs.toLong())
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun setupButtons() {
        binding.btnOkay.setOnClickListener {
            FileLogger.log(this, TAG, "User clicked Okay - restoring to normal")
            restoreToNormal()
            finish()
        }

        binding.btnDismiss.setOnClickListener {
            FileLogger.log(this, TAG, "User clicked Dismiss - hiding dialog only")
            finish()
        }

        binding.btnSnooze10.setOnClickListener {
            snoozeAgain(10L)
        }

        binding.btnSnooze15.setOnClickListener {
            snoozeAgain(15L)
        }

        binding.btnSnooze20.setOnClickListener {
            snoozeAgain(20L)
        }

        binding.btnSnooze30.setOnClickListener {
            snoozeAgain(30L)
        }
    }

    private fun restoreToNormal() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 1. Switch off DND
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    FileLogger.log(this, TAG, "DND OFF")
                }
            }

            // 2. Set ringer mode to NORMAL
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            FileLogger.log(this, TAG, "Ringer mode: NORMAL")

            // 3. Set alarm and notification volume to 80% of max
            val maxAlarmVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val alarmVol = (maxAlarmVol * 0.8).toInt()
            am.setStreamVolume(AudioManager.STREAM_ALARM, alarmVol, 0)
            FileLogger.log(this, TAG, "Alarm volume set to $alarmVol (80% of max $maxAlarmVol)")

            val maxNotifVol = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val notifVol = (maxNotifVol * 0.8).toInt()
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifVol, 0)
            FileLogger.log(this, TAG, "Notification volume set to $notifVol (80% of max $maxNotifVol)")

            val maxRingVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            val ringVol = (maxRingVol * 0.8).toInt()
            am.setStreamVolume(AudioManager.STREAM_RING, ringVol, 0)
            FileLogger.log(this, TAG, "Ring volume set to $ringVol (80% of max $maxRingVol)")

            // 4. Clear snooze state
            repository.clearSnooze()
            NotificationHelper.cancelOngoingSnoozeNotification(this)

            FileLogger.log(this, TAG, "Restore to normal completed successfully")
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Failed to restore to normal", e)
        }
    }

    private fun snoozeAgain(minutes: Long) {
        FileLogger.log(this, TAG, "User requested snooze again for $minutes minutes")
        
        try {
            // Keep current mute/vibration state (reapply silent mode)
            SilentController.applySilent(this)

            val durationMillis = TimeUnit.MINUTES.toMillis(minutes)
            val endTimestamp = System.currentTimeMillis() + durationMillis
            val isShortSnooze = minutes <= 15

            repository.saveSnooze(
                endTimestamp,
                wantsMute = true,
                wantsVibrationOff = true,
                isShortSnooze = isShortSnooze
            )

            // Schedule alarm: <= 15 min = exact alarm, > 15 min = WorkManager
            if (isShortSnooze) {
                FileLogger.log(this, TAG, "SHORT snooze ($minutes min) - using EXACT ALARM")
                RestoreWorker.scheduleExactAlarm(this, durationMillis)
            } else {
                FileLogger.log(this, TAG, "LONG snooze ($minutes min) - using WorkManager")
                RestoreWorker.schedule(this, durationMillis)
            }

            val formattedTime = android.text.format.DateFormat.getTimeFormat(this)
                .format(java.util.Date(endTimestamp))
            NotificationHelper.showOngoingSnoozeNotification(this, formattedTime)

            android.widget.Toast.makeText(
                this,
                "Snoozing again for $minutes minutes",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            FileLogger.log(this, TAG, "Snooze again scheduled successfully")
            finish()
        } catch (e: Exception) {
            FileLogger.log(this, TAG, "Failed to snooze again", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.release()
        audioTrack = null
        FileLogger.log(this, TAG, "Popup destroyed")
    }
}
