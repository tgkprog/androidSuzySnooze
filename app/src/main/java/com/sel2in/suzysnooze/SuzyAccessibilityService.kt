package com.sel2in.suzysnooze

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class SuzyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service is required for certain system-level audio controls
        // No active event handling needed for basic functionality
    }

    override fun onInterrupt() {
        FileLogger.log(this, "AccessibilityService", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FileLogger.log(this, "AccessibilityService", "Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        FileLogger.log(this, "AccessibilityService", "Service destroyed")
    }
}
