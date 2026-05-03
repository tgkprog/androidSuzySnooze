package com.sel2in.suzysnooze

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class SuzyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        FileLogger.log(context, "DeviceAdmin", "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        FileLogger.log(context, "DeviceAdmin", "Device Admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        FileLogger.log(context, "DeviceAdmin", "Device Admin disable requested")
        return "Suzy Snooze uses Device Administrator to manage Do Not Disturb mode and mute ringer/notifications during snooze. No data is collected or transmitted off-device."
    }
}
