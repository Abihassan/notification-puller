package com.notificationpuller.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import com.notificationpuller.notification.PullerNotificationListenerService

class NotificationListenerBootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationPuller"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, PullerNotificationListenerService::class.java)
                )
                Log.d(TAG, "Requested notification listener rebind after boot")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to request notification listener rebind", error)
            }
        }
    }
}
