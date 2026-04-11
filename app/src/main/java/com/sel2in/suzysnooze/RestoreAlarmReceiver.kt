package com.sel2in.suzysnooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for exact alarm (< 15 min snoozes).
 * Performs same restore logic as RestoreWorker.
 */
class RestoreAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "RestoreAlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        FileLogger.log(context, TAG, "=== EXACT ALARM TRIGGERED ===")
        
        try {
            val repo = SnoozeRepository(context)
            
            // Check if snooze is still active
            if (!repo.isSnoozeActive()) {
                FileLogger.log(context, TAG, "Snooze not active, skipping restore")
                return
            }
            
            FileLogger.log(context, TAG, "Restoring from exact alarm...")
            
            SilentController.restoreState(context)
            NotificationHelper.cancelOngoingSnoozeNotification(context)
            repo.clearSnooze()
            
            FileLogger.log(context, TAG, "Exact alarm restore completed successfully")
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Exact alarm restore failed", e)
        }
    }
}
