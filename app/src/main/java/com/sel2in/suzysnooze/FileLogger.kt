package com.sel2in.suzysnooze

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logs to external storage: /sdcard/Android/data/com.sel2in.suzysnooze/files/snooze.log
 * This persists after uninstall and is accessible via file managers.
 */
object FileLogger {
    
    private const val TAG = "FileLogger"
    private const val LOG_FILE_NAME = "snooze.log"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    /**
     * Log a message to file and logcat.
     */
    fun log(context: Context, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $tag: $message\n"
        
        // Also log to logcat
        Log.d(tag, message)
        
        try {
            val logFile = getLogFile(context) ?: return
            
            // Check size and rotate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                val backupFile = File(logFile.parentFile, "$LOG_FILE_NAME.old")
                logFile.renameTo(backupFile)
            }
            
            logFile.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
    
    /**
     * Log with exception.
     */
    fun log(context: Context, tag: String, message: String, throwable: Throwable) {
        log(context, tag, "$message: ${throwable.message}\n${Log.getStackTraceString(throwable)}")
    }
    
    /**
     * Get log file path.
     */
    private fun getLogFile(context: Context): File? {
        return try {
            // /sdcard/Android/data/com.sel2in.suzysnooze/files/
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                File(externalFilesDir, LOG_FILE_NAME)
            } else {
                Log.e(TAG, "External files directory is null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get log file", e)
            null
        }
    }
    
    /**
     * Get log file path as string for display.
     */
    fun getLogFilePath(context: Context): String {
        return getLogFile(context)?.absolutePath ?: "Log file not available"
    }
}
