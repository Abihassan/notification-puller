package com.notificationpuller.system

import android.content.Context

class NotificationServiceStateStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun setConnected(connected: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONNECTED, connected)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }

    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false)

    fun getLastStateChange(): Long = prefs.getLong(KEY_LAST_STATE_CHANGE, 0L)

    companion object {
        private const val PREFS_NAME = "notification_service_state"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_LAST_STATE_CHANGE = "last_state_change"

        @Volatile
        private var instance: NotificationServiceStateStore? = null

        fun getInstance(context: Context): NotificationServiceStateStore =
            instance ?: synchronized(this) {
                instance ?: NotificationServiceStateStore(context).also { instance = it }
            }
    }
}
