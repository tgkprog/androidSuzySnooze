package com.sel2in.suzysnooze

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RestoreWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    companion object {
        private const val TAG = "RestoreWorker"
        private const val WORK_NAME = "restore_volume"
        private const val ALARM_REQUEST_CODE = 2015

        /**
         * Schedule exact alarm for short snoozes (<= 15 min).
         * Uses setExactAndAllowWhileIdle for precise timing even in Doze mode.
         */
        fun scheduleExactAlarm(context: Context, delayMillis: Long) {
            FileLogger.log(context, TAG, "Scheduling EXACT ALARM for ${delayMillis}ms (${delayMillis/60000} minutes)")
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RestoreAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + delayMillis
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // setExactAndAllowWhileIdle bypasses Doze mode restrictions
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
                FileLogger.log(context, TAG, "Exact alarm scheduled successfully for ${java.util.Date(triggerAtMillis)}")
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to schedule exact alarm", e)
            }
        }

        /**
         * Schedule WorkManager for long snoozes (> 15 min).
         */
        fun schedule(context: Context, delayMillis: Long) {
            FileLogger.log(context, TAG, "Scheduling WORKMANAGER for ${delayMillis}ms")
            
            val workRequest = OneTimeWorkRequestBuilder<RestoreWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
            
            FileLogger.log(context, TAG, "WorkManager scheduled successfully")
        }

        /**
         * Cancel exact alarm.
         */
        fun cancelExactAlarm(context: Context) {
            FileLogger.log(context, TAG, "Cancelling exact alarm")
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RestoreAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        /**
         * Cancel WorkManager.
         */
        fun cancel(context: Context) {
            FileLogger.log(context, TAG, "Cancelling WorkManager")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        FileLogger.log(applicationContext, TAG, "=== WORKMANAGER TRIGGERED ===")
        
        try {
            val repo = SnoozeRepository(applicationContext)
            
            // Check if snooze is still active
            if (!repo.isSnoozeActive()) {
                FileLogger.log(applicationContext, TAG, "Snooze not active, skipping restore")
                return Result.success()
            }
            
            FileLogger.log(applicationContext, TAG, "Showing restore dialog...")
            
            // Show popup dialog for user to choose action
            RestorePopupActivity.show(applicationContext)
            
            FileLogger.log(applicationContext, TAG, "WorkManager triggered - dialog shown")
        } catch (e: Exception) {
            FileLogger.log(applicationContext, TAG, "WorkManager restore failed", e)
        }
        return Result.success()
    }
}
