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
 * Silent controller with DND priority policy:
 * - Set ring + notification to 0
 * - Use DND PRIORITY mode (allows alarms, blocks calls/notifications)
 * - Keeps alarm and media volumes intact
 */
object SilentController {
    
    private const val TAG = "SilentController"
    private const val PREFS_NAME = "silent_controller"
    
    private const val KEY_VALID = "valid"
    private const val KEY_RING_VOLUME = "ring_volume"
    private const val KEY_NOTIFICATION_VOLUME = "notification_volume"
    private const val KEY_ALARM_VOLUME = "alarm_volume"
    private const val KEY_MEDIA_VOLUME = "media_volume"
    private const val KEY_RINGER_MODE = "ringer_mode"
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
        val ringerMode = am.ringerMode
        
        prefs.edit {
            putBoolean(KEY_VALID, true)
            putInt(KEY_RING_VOLUME, ringVol)
            putInt(KEY_NOTIFICATION_VOLUME, notifVol)
            putInt(KEY_ALARM_VOLUME, alarmVol)
            putInt(KEY_MEDIA_VOLUME, mediaVol)
            putInt(KEY_RINGER_MODE, ringerMode)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = nm.currentInterruptionFilter
                putInt(KEY_INTERRUPTION_FILTER, filter)
                FileLogger.log(context, TAG, "Saved: ring=$ringVol, notif=$notifVol, alarm=$alarmVol, media=$mediaVol, mode=$ringerMode, dnd=$filter")
            } else {
                FileLogger.log(context, TAG, "Saved: ring=$ringVol, notif=$notifVol, alarm=$alarmVol, media=$mediaVol, mode=$ringerMode")
            }
        }
    }
    
    /**
     * Apply silent mode using DND PRIORITY with custom policy.
     */
    fun applySilent(context: Context) {
        FileLogger.log(context, TAG, "=== APPLYING SILENT MODE WITH DND PRIORITY ===")
        
        saveState(context)
        
        // Run on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // STEP 1: Set ring + notification to 0
                am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                FileLogger.log(context, TAG, "Step 1a: Set ring volume to 0")
                
                delay(100)
                
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                FileLogger.log(context, TAG, "Step 1b: Set notification volume to 0")
                
                delay(200)
                
                // STEP 2: Set ringer mode to SILENT (no vibrate)
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                FileLogger.log(context, TAG, "Step 2: Set ringer mode to SILENT (no vibrate)")
                
                delay(200)
                
                // STEP 3: Create custom DND policy that ALLOWS ALARMS and MEDIA
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        // Save if we're setting a custom policy
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit { putBoolean(KEY_HAD_CUSTOM_POLICY, true) }
                        
                        // Create policy: Allow alarms and media, block everything else
                        val policy = NotificationManager.Policy(
                            NotificationManager.POLICY_PRIORITY_CATEGORY_ALARMS or
                            NotificationManager.POLICY_PRIORITY_CATEGORY_MEDIA,
                            0, // No priority callers
                            0  // No priority messages
                        )
                        nm.setNotificationPolicy(policy)
                        FileLogger.log(context, TAG, "Step 3a: Set custom policy (allow alarms + media)")
                        
                        delay(200)
                        
                        // Set DND to PRIORITY mode (not NONE!)
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        FileLogger.log(context, TAG, "Step 3b: Set DND to PRIORITY mode")
                    } else {
                        FileLogger.log(context, TAG, "WARNING: No DND permission!")
                    }
                }
                
                // Verify final state
                delay(300)
                val finalRingVol = am.getStreamVolume(AudioManager.STREAM_RING)
                val finalNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val finalAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val finalMediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val finalMode = am.ringerMode
                
                val modeStr = when (finalMode) {
                    AudioManager.RINGER_MODE_SILENT -> "SILENT"
                    AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                    AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                    else -> "UNKNOWN($finalMode)"
                }
                
                FileLogger.log(context, TAG, "FINAL STATE: mode=$modeStr, ring=$finalRingVol, notif=$finalNotifVol, alarm=$finalAlarmVol, media=$finalMediaVol")
                
                if (finalAlarmVol == 0 || finalMediaVol == 0) {
                    FileLogger.log(context, TAG, "ERROR: Alarm or Media is still at 0!")
                } else {
                    FileLogger.log(context, TAG, "SUCCESS: Alarm and Media preserved!")
                }
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to apply silent", e)
            }
        }
    }
    
    /**
     * Restore saved state (only if user hasn't manually adjusted volumes).
     */
    fun restoreState(context: Context) {
        FileLogger.log(context, TAG, "=== RESTORING STATE ===")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_VALID, false)) {
            FileLogger.log(context, TAG, "No saved state to restore")
            return
        }
        
        val savedRingVol = prefs.getInt(KEY_RING_VOLUME, 0)
        val savedNotifVol = prefs.getInt(KEY_NOTIFICATION_VOLUME, 0)
        val savedAlarmVol = prefs.getInt(KEY_ALARM_VOLUME, 0)
        val savedMediaVol = prefs.getInt(KEY_MEDIA_VOLUME, 0)
        val savedRingerMode = prefs.getInt(KEY_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
        val savedFilter = prefs.getInt(KEY_INTERRUPTION_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        val hadCustomPolicy = prefs.getBoolean(KEY_HAD_CUSTOM_POLICY, false)
        
        // Run on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Check current volumes - if user adjusted, don't revert
                val currentRingVol = am.getStreamVolume(AudioManager.STREAM_RING)
                val currentNotifVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val currentAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val currentMediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                
                FileLogger.log(context, TAG, "Current volumes: ring=$currentRingVol, notif=$currentNotifVol, alarm=$currentAlarmVol, media=$currentMediaVol")
                
                // Step 1: Restore DND filter first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(savedFilter)
                        FileLogger.log(context, TAG, "Restored DND to filter=$savedFilter")
                        
                        // If we set a custom policy, we should restore the default policy
                        if (hadCustomPolicy) {
                            // Reset to default "allow all" policy
                            val defaultPolicy = NotificationManager.Policy(
                                NotificationManager.POLICY_PRIORITY_CATEGORY_ALARMS or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_MEDIA or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_SYSTEM or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_MESSAGES or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_CALLS or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_REMINDERS or
                                NotificationManager.POLICY_PRIORITY_CATEGORY_EVENTS,
                                NotificationManager.POLICY_PRIORITY_SENDERS_ANY,
                                NotificationManager.POLICY_PRIORITY_SENDERS_ANY
                            )
                            nm.setNotificationPolicy(defaultPolicy)
                            FileLogger.log(context, TAG, "Restored default DND policy")
                        }
                    }
                }
                
                delay(200)
                
                // Step 2: Restore ringer mode
                am.ringerMode = savedRingerMode
                FileLogger.log(context, TAG, "Restored ringer mode to $savedRingerMode")
                
                delay(200)
                
                // Step 3: Restore ring volume (only if still at 0)
                if (currentRingVol == 0) {
                    am.setStreamVolume(AudioManager.STREAM_RING, savedRingVol, 0)
                    FileLogger.log(context, TAG, "Restored ring volume to $savedRingVol")
                } else {
                    FileLogger.log(context, TAG, "Ring volume changed by user ($currentRingVol), not restoring")
                }
                
                delay(200)
                
                // Step 4: Restore notification volume (only if still at 0)
                if (currentNotifVol == 0) {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedNotifVol, 0)
                    FileLogger.log(context, TAG, "Restored notification volume to $savedNotifVol")
                } else {
                    FileLogger.log(context, TAG, "Notification volume changed by user ($currentNotifVol), not restoring")
                }
                
                delay(200)
                
                // Step 5: Restore alarm (only if at 0)
                if (currentAlarmVol == 0) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0)
                    FileLogger.log(context, TAG, "Restored alarm volume to $savedAlarmVol")
                } else {
                    FileLogger.log(context, TAG, "Alarm volume OK ($currentAlarmVol)")
                }
                
                delay(200)
                
                // Step 6: Restore media (only if at 0)
                if (currentMediaVol == 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMediaVol, 0)
                    FileLogger.log(context, TAG, "Restored media volume to $savedMediaVol")
                } else {
                    FileLogger.log(context, TAG, "Media volume OK ($currentMediaVol)")
                }
                
                // Clear state
                withContext(Dispatchers.Main) {
                    prefs.edit { clear() }
                }
                FileLogger.log(context, TAG, "Restore complete, cleared saved state")
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
