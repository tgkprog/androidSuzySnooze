package com.sel2in.suzysnooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CancelSnoozeReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CancelSnoozeReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        FileLogger.log(context, TAG, "=== CANCEL BUTTON PRESSED ===")
        
        try {
            val repo = SnoozeRepository(context)
            if (!repo.isSnoozeActive()) {
                FileLogger.log(context, TAG, "No active snooze to cancel")
                return
            }

            // Cancel both exact alarm and WorkManager
            RestoreWorker.cancelExactAlarm(context)
            RestoreWorker.cancel(context)
            
            SilentController.restoreState(context)
            repo.clearSnooze()
            NotificationHelper.cancelOngoingSnoozeNotification(context)
            
            FileLogger.log(context, TAG, "Snooze cancelled successfully")
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Cancel failed", e)
        }
    }
}
