package com.notificationpuller.notification

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notificationpuller.bridge.NotificationModule
import com.notificationpuller.database.NotificationEntity
import com.notificationpuller.database.NotificationRepository
import com.notificationpuller.filter.NotificationFilterStore
import com.notificationpuller.system.NotificationServiceStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PullerNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationPuller"
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private lateinit var repository: NotificationRepository
    private lateinit var filterStore: NotificationFilterStore
    private lateinit var stateStore: NotificationServiceStateStore

    override fun onCreate() {
        super.onCreate()
        repository = NotificationRepository.getInstance(applicationContext)
        filterStore = NotificationFilterStore.getInstance(applicationContext)
        stateStore = NotificationServiceStateStore.getInstance(applicationContext)
        Log.d(TAG, "Notification listener service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        stateStore.setConnected(true)
        Log.d(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val isSystemApp = isSystemPackage(packageName)

        if (!filterStore.isPackageEnabled(packageName, isSystemApp)) {
            Log.d(TAG, "Notification ignored by app filter: $packageName")
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""
        val category = notification.category ?: ""
        val timestamp = sbn.postTime
        val notificationKey = sbn.key
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val groupKey = notification.group ?: ""
        val isGroupSummary =
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        Log.d(TAG, "Notification posted/updated: $packageName / $title")

        val incoming = NotificationEntity(
            id = "",
            notificationKey = notificationKey,
            packageName = packageName,
            title = title,
            text = text,
            subText = subText,
            category = category,
            timestamp = timestamp,
            isOngoing = isOngoing,
            createdAt = System.currentTimeMillis(),
            groupKey = groupKey,
            isGroupSummary = isGroupSummary
        )

        serviceScope.launch {
            try {
                val existing = repository.getByNotificationKey(notificationKey)
                val saved = repository.save(incoming)

                // A notification key identifies one Android notification.
                // Only the first observation is a true `posted` event; any
                // later observation is an update/re-post of the existing
                // archived record and must not increase the UI's total count.
                val eventType = if (existing == null) {
                    "posted"
                } else {
                    "updated"
                }

                NotificationModule.sendNotification(saved, eventType)
                Log.d(TAG, "Saved notification. id=${saved.id}, event=$eventType")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to save notification in Room", error)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val notificationKey = sbn.key
        val removedAt = System.currentTimeMillis()

        Log.d(TAG, "Notification removed: ${sbn.packageName}, key=$notificationKey")

        serviceScope.launch {
            try {
                val removed = repository.markRemoved(
                    notificationKey = notificationKey,
                    removedAt = removedAt
                )

                if (removed != null) {
                    NotificationModule.sendNotification(removed, "removed")
                    Log.d(TAG, "Marked notification removed: ${removed.id}")
                } else {
                    Log.d(TAG, "Removed notification was not stored: $notificationKey")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to mark notification removed", error)
            }
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        } catch (_: Exception) {
            packageName == "android" || packageName.startsWith("com.android.")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        stateStore.setConnected(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                requestRebind(android.content.ComponentName(this, PullerNotificationListenerService::class.java))
                Log.d(TAG, "Requested notification listener rebind after disconnect")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to request listener rebind", error)
            }
        }
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        stateStore.setConnected(false)
        serviceScope.cancel()
        Log.d(TAG, "Notification listener service destroyed")
        super.onDestroy()
    }
}
