package com.notificationpuller.system

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.notificationpuller.notification.PullerNotificationListenerService

object NotificationServiceHealth {
    fun isListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val component = ComponentName(context, PullerNotificationListenerService::class.java)
        return enabled.split(":").any { value ->
            ComponentName.unflattenFromString(value) == component
        }
    }

    fun isServiceConnected(context: Context): Boolean =
        NotificationServiceStateStore.getInstance(context).isConnected()

    fun lastStateChange(context: Context): Long =
        NotificationServiceStateStore.getInstance(context).getLastStateChange()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun openNotificationListenerSettings(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        )
    }

    fun openBatteryOptimizationSettings(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }
}
