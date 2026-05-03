package com.sel2in.suzysnooze

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Silent controller with DND:
 * - DND ON (PRIORITY mode with policy: allow alarms + media ONLY)
 * - Set ring + notification volumes to 0
 * - Set ringer mode to SILENT (no vibrate)
 * - Media and alarms NEVER touched - remain fully functional
 * - Restore: DND OFF, FORCE NORMAL mode, restore ring/notification volumes (min 3)
 */
object SilentController {
    
    private const val TAG = "SilentController"
    private const val PREFS_NAME = "silent_controller"
    
    private const val KEY_VALID = "valid"
    private const val KEY_RING_VOLUME = "ring_volume"
    private const val KEY_NOTIFICATION_VOLUME = "notification_volume"
    private const val KEY_ALARM_VOLUME = "alarm_volume"
    private const val KEY_MEDIA_VOLUME = "media_volume"
    private const val KEY_INTERRUPTION_FILTER = "interruption_filter"
    private const val KEY_HAD_CUSTOM_POLICY = "had_custom_policy"
    
    /**
     * Save current audio state.
     */
    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_VALID, false)) {
            FileLogger.log(context, TAG, "State already saved, skipping save")
            return
        }
        
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val ringVol = am.getStreamVolume(AudioManager.STREAM_RING)
        val notifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        prefs.edit {
            putBoolean(KEY_VALID, true)
            putInt(KEY_RING_VOLUME, ringVol)
            putInt(KEY_NOTIFICATION_VOLUME, notifVol)
            putInt(KEY_ALARM_VOLUME, alarmVol)
            putInt(KEY_MEDIA_VOLUME, mediaVol)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = nm.currentInterruptionFilter
                putInt(KEY_INTERRUPTION_FILTER, filter)
                FileLogger.log(context, TAG, "Saved: ring=$ringVol, notif=$notifVol, alarm=$alarmVol, media=$mediaVol, dnd=$filter")
            } else {
                FileLogger.log(context, TAG, "Saved: ring=$ringVol, notif=$notifVol, alarm=$alarmVol, media=$mediaVol")
            }
        }
    }
    
    /**
     * Apply silent mode: DND ON, block calls+notifications (no vibrate), keep alarms+media untouched.
     */
    fun applySilent(context: Context) {
        FileLogger.log(context, TAG, "=== APPLYING SILENT MODE ===")
        
        saveState(context)
        
        // Run on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // STEP 1: Enable DND PRIORITY mode (blocks calls + notifications, allows alarms + media)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        // Set policy: Allow ONLY alarms and media, block everything else
                        // This prevents DND from silencing media playback
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit { putBoolean(KEY_HAD_CUSTOM_POLICY, true) }
                            
                            val policy = NotificationManager.Policy(
                                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, // Allow alarms + media
                                0, // No priority callers
                                0  // No priority messages
                            )
                            nm.setNotificationPolicy(policy)
                            FileLogger.log(context, TAG, "Set policy: allow alarms + media, block calls/notifications")
                        }
                        
                        delay(100)
                        
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        FileLogger.log(context, TAG, "DND ON (PRIORITY mode)")
                    } else {
                        FileLogger.log(context, TAG, "WARNING: No DND permission!")
                    }
                }
                
                delay(200)
                
                // STEP 2: Force ringer to SILENT (no vibration)
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                FileLogger.log(context, TAG, "Ringer mode: SILENT (no vibrate)")
                
                delay(100)
                
                // STEP 3: Mute ring + notification volumes
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                FileLogger.log(context, TAG, "Ring volume: 0")
                
                delay(100)
                
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                FileLogger.log(context, TAG, "Notification volume: 0")
                
                // DO NOT TOUCH: STREAM_ALARM, STREAM_MUSIC
                
                // Verify final state
                delay(300)
                val finalRingVol = am.getStreamVolume(AudioManager.STREAM_RING)
                val finalNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val finalAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val finalMediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val finalMode = am.ringerMode
                val finalDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    nm.currentInterruptionFilter
                } else {
                    -1
                }
                
                val modeStr = when (finalMode) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                    else -> "UNKNOWN($finalMode)"
                }
                
                FileLogger.log(context, TAG, "FINAL: mode=$modeStr, DND=$finalDnd, ring=$finalRingVol, notif=$finalNotifVol, alarm=$finalAlarmVol, media=$finalMediaVol")
                
                if (finalAlarmVol == 0 || finalMediaVol == 0) {
                    FileLogger.log(context, TAG, "WARNING: Alarm or Media volume is 0!")
                } else {
                    FileLogger.log(context, TAG, "SUCCESS: Alarms and Media preserved")
                }
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to apply silent", e)
            }
        }
    }
    
    /**
     * Restore saved state: DND OFF, restore volumes (min 3), FORCE NORMAL mode.
     */
    fun restoreState(context: Context) {
        FileLogger.log(context, TAG, "=== RESTORING STATE ===")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_VALID, false)) {
            FileLogger.log(context, TAG, "No saved state to restore")
            return
        }
        
        val savedRingVol = prefs.getInt(KEY_RING_VOLUME, 5)
        val savedNotifVol = prefs.getInt(KEY_NOTIFICATION_VOLUME, 5)
        val savedAlarmVol = prefs.getInt(KEY_ALARM_VOLUME, 5)
        val savedMediaVol = prefs.getInt(KEY_MEDIA_VOLUME, 5)
        val savedFilter = prefs.getInt(KEY_INTERRUPTION_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        val hadCustomPolicy = prefs.getBoolean(KEY_HAD_CUSTOM_POLICY, false)
        
        // Run on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // STEP 1: DND OFF
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        FileLogger.log(context, TAG, "DND OFF")
                        
                        // Restore default policy if we set a custom one
                        if (hadCustomPolicy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val defaultPolicy = NotificationManager.Policy(
                                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                                NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM or
                                NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES or
                                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                                NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS or
                                NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS,
                                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                                NotificationManager.Policy.PRIORITY_SENDERS_ANY
                            )
                            nm.setNotificationPolicy(defaultPolicy)
                            FileLogger.log(context, TAG, "Restored default policy")
                        }
                    }
                }
                
                delay(200)
                
                // STEP 2: FORCE NORMAL mode (critical - prevents stuck in silent/vibrate)
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                FileLogger.log(context, TAG, "Ringer mode: NORMAL (FORCED)")
                
                delay(200)
                
                // STEP 3: Restore volumes safely (minimum 3 to ensure audible)
                val ringVol = Math.max(savedRingVol, 3)
                val notifVol = Math.max(savedNotifVol, 3)
                
                am.setStreamVolume(AudioManager.STREAM_RING, ringVol, 0)
                FileLogger.log(context, TAG, "Ring volume: $ringVol (saved: $savedRingVol)")
                
                delay(100)
                
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifVol, 0)
                FileLogger.log(context, TAG, "Notification volume: $notifVol (saved: $savedNotifVol)")
                
                delay(100)
                
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0)
                FileLogger.log(context, TAG, "Alarm volume: $savedAlarmVol")
                
                delay(100)
                
                // Only restore media if it was previously at 0 (unlikely)
                val currentMediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentMediaVol == 0 && savedMediaVol > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVol, 0)
                    FileLogger.log(context, TAG, "Media volume: $savedMediaVol (was 0)")
                }
                
                // Clear state
                withContext(Dispatchers.Main) {
                    prefs.edit { clear() }
                }
                
                // Verify final state
                delay(300)
                val finalMode = am.ringerMode
                val finalRingVol = am.getStreamVolume(AudioManager.STREAM_RING)
                val finalNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val finalDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    nm.currentInterruptionFilter
                } else {
                    -1
                }
                
                val modeStr = when (finalMode) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                    else -> "UNKNOWN($finalMode)"
                }
                
                FileLogger.log(context, TAG, "FINAL: mode=$modeStr, DND=$finalDnd, ring=$finalRingVol, notif=$finalNotifVol")
                
                if (finalMode != AudioManager.RINGER_MODE_NORMAL) {
                    FileLogger.log(context, TAG, "WARNING: Ringer mode not NORMAL! May need manual fix.")
                } else if (finalRingVol < 3 || finalNotifVol < 3) {
                    FileLogger.log(context, TAG, "WARNING: Volumes still too low!")
                } else {
                    FileLogger.log(context, TAG, "SUCCESS: Restore complete")
                }
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to restore", e)
            }
        }
    }
    
    fun hasSavedState(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VALID, false)
    }
}
