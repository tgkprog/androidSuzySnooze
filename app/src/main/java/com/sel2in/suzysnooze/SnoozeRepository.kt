package com.sel2in.suzysnooze

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

class SnoozeRepository(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnooze(
        endTimeMillis: Long,
        wantsMute: Boolean,
        wantsVibrationOff: Boolean,
        isShortSnooze: Boolean
    ) {
        preferences.edit {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_END_TIME, endTimeMillis)
            putBoolean(KEY_WANTS_MUTE, wantsMute)
            putBoolean(KEY_WANTS_VIBRATION, wantsVibrationOff)
            putBoolean(KEY_IS_SHORT_SNOOZE, isShortSnooze)
        }
    }

    fun clearSnooze() {
        preferences.edit {
            putBoolean(KEY_ACTIVE, false)
            putLong(KEY_END_TIME, 0L)
            putBoolean(KEY_WANTS_MUTE, false)
            putBoolean(KEY_WANTS_VIBRATION, false)
            putBoolean(KEY_IS_SHORT_SNOOZE, false)
        }
    }

    fun isSnoozeActive(): Boolean = preferences.getBoolean(KEY_ACTIVE, false)

    fun snoozeEndTime(): Long = preferences.getLong(KEY_END_TIME, 0L)

    fun wantsMute(): Boolean = preferences.getBoolean(KEY_WANTS_MUTE, false)

    fun wantsVibrationOff(): Boolean = preferences.getBoolean(KEY_WANTS_VIBRATION, false)

    fun isShortSnooze(): Boolean = preferences.getBoolean(KEY_IS_SHORT_SNOOZE, false)

    /**
     * Get remaining time in minutes (rounded up).
     */
    fun remainingMinutes(): Long {
        val endTime = snoozeEndTime()
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) {
            TimeUnit.MILLISECONDS.toMinutes(remaining) + 1 // Round up
        } else {
            0
        }
    }

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "snooze_repo"
        private const val KEY_ACTIVE = "key_active"
        /** Exposed so observers can filter SharedPreference change callbacks by key. */
        const val KEY_ACTIVE_PUBLIC = KEY_ACTIVE
        private const val KEY_END_TIME = "key_end_time"
        private const val KEY_WANTS_MUTE = "key_wants_mute"
        private const val KEY_WANTS_VIBRATION = "key_wants_vibration"
        private const val KEY_IS_SHORT_SNOOZE = "key_is_short_snooze"
    }
}
