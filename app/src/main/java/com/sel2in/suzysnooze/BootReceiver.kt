package com.sel2in.suzysnooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import java.util.Date

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        FileLogger.log(context, TAG, "=== BOOT COMPLETED ===")
        
        try {
            val repository = SnoozeRepository(context)
            val endTime = repository.snoozeEndTime()
            val remaining = endTime - System.currentTimeMillis()

            if (repository.isSnoozeActive() && remaining > 0) {
                // Re-schedule based on short/long snooze
                if (repository.isShortSnooze()) {
                    FileLogger.log(context, TAG, "Re-scheduling SHORT snooze (exact alarm)")
                    RestoreWorker.scheduleExactAlarm(context, remaining)
                } else {
                    FileLogger.log(context, TAG, "Re-scheduling LONG snooze (WorkManager)")
                    RestoreWorker.schedule(context, remaining)
                }
                
                val formattedTime = DateFormat.getTimeFormat(context).format(Date(endTime))
                NotificationHelper.showOngoingSnoozeNotification(context, formattedTime)
                
                FileLogger.log(context, TAG, "Snooze re-scheduled successfully until $formattedTime")
            } else if (repository.isSnoozeActive()) {
                // Snooze expired while device was off
                FileLogger.log(context, TAG, "Snooze expired during boot, restoring now")
                
                if (SilentController.hasSavedState(context)) {
                    SilentController.restoreState(context)
                }
                NotificationHelper.cancelOngoingSnoozeNotification(context)
                repository.clearSnooze()
            } else {
                FileLogger.log(context, TAG, "No active snooze to restore")
            }
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Boot handling failed", e)
        }
    }
}
