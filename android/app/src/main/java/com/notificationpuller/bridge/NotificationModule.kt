package com.notificationpuller.bridge

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.notificationpuller.database.NotificationEntity
import com.notificationpuller.database.NotificationRepository
import com.notificationpuller.export.NotificationExportManager
import com.notificationpuller.filter.NotificationAppInfo
import com.notificationpuller.filter.NotificationFilterStore
import com.notificationpuller.system.NotificationServiceHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import android.util.Base64
import java.util.UUID

class NotificationModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        @Volatile
        private var instance: NotificationModule? = null

        fun sendNotification(
            notification: NotificationEntity,
            eventType: String = "posted"
        ) {
            instance?.emitNotification(notification, eventType)
        }
    }

    private val moduleScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val repository = NotificationRepository.getInstance(
        reactContext.applicationContext
    )

    private val filterStore = NotificationFilterStore.getInstance(
        reactContext.applicationContext
    )

    private val exportManager = NotificationExportManager(
        reactContext.applicationContext
    )

    init {
        instance = this
    }

    override fun getName(): String = "NotificationModule"

    /**
     * Returns one page of notifications ordered newest first.
     * limit is clamped to 1..500 by the repository.
     */
    @ReactMethod
    fun getNotifications(
        limit: Int,
        offset: Int,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val page = repository.getPage(limit, offset)
                val result = Arguments.createMap()
                val items = Arguments.createArray()

                page.items.forEach { items.pushMap(toWritableMap(it)) }

                result.putArray("items", items)
                result.putInt("total", page.total)
                result.putInt("limit", page.limit)
                result.putInt("offset", page.offset)
                result.putBoolean("hasMore", page.hasMore)

                promise.resolve(result)
            } catch (error: Exception) {
                promise.reject(
                    "GET_NOTIFICATIONS_FAILED",
                    "Failed to load notifications",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun searchNotifications(
        limit: Int,
        offset: Int,
        search: String,
        packageName: String,
        unreadOnly: Boolean,
        activeOnly: Boolean,
        fromTime: Double,
        toTime: Double,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val page = repository.getPage(
                    limit = limit,
                    offset = offset,
                    search = search,
                    packageName = packageName,
                    unreadOnly = unreadOnly,
                    activeOnly = activeOnly,
                    fromTime = fromTime.toLong(),
                    toTime = toTime.toLong()
                )
                val result = Arguments.createMap()
                val items = Arguments.createArray()
                page.items.forEach { items.pushMap(toWritableMap(it)) }
                result.putArray("items", items)
                result.putInt("total", page.total)
                result.putInt("limit", page.limit)
                result.putInt("offset", page.offset)
                result.putBoolean("hasMore", page.hasMore)
                promise.resolve(result)
            } catch (error: Exception) {
                promise.reject("SEARCH_NOTIFICATIONS_FAILED", "Failed to search/filter notifications", error)
            }
        }
    }

    @ReactMethod
    fun getUnreadCount(promise: Promise) {
        moduleScope.launch {
            try { promise.resolve(repository.unreadCount()) }
            catch (error: Exception) { promise.reject("GET_UNREAD_COUNT_FAILED", "Failed to count unread notifications", error) }
        }
    }

    @ReactMethod
    fun setNotificationRead(id: String, isRead: Boolean, promise: Promise) {
        moduleScope.launch {
            try { promise.resolve(repository.setRead(id, isRead)) }
            catch (error: Exception) { promise.reject("SET_READ_FAILED", "Failed to update notification read state", error) }
        }
    }

    @ReactMethod
    fun getNotification(
        id: String,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val notification = repository.getById(id)
                promise.resolve(notification?.let(::toWritableMap))
            } catch (error: Exception) {
                promise.reject(
                    "GET_NOTIFICATION_FAILED",
                    "Failed to load notification",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun getCount(promise: Promise) {
        moduleScope.launch {
            try {
                promise.resolve(repository.count())
            } catch (error: Exception) {
                promise.reject(
                    "GET_COUNT_FAILED",
                    "Failed to count notifications",
                    error
                )
            }
        }
    }

    /**
     * Saves a notification. notificationKey is the Android notification's
     * stable identity. If it already exists, the existing row is updated
     * while preserving its local id and createdAt.
     */
    @ReactMethod
    fun saveNotification(
        data: ReadableMap,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val notification = fromReadableMap(data)
                val saved = repository.save(notification)
                promise.resolve(toWritableMap(saved))
            } catch (error: Exception) {
                promise.reject(
                    "SAVE_NOTIFICATION_FAILED",
                    "Failed to save notification",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun updateNotification(
        data: ReadableMap,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val notification = fromReadableMap(data)

                if (notification.id.isBlank()) {
                    promise.reject(
                        "INVALID_NOTIFICATION_ID",
                        "updateNotification requires a non-empty id"
                    )
                    return@launch
                }

                val updated = repository.update(notification)
                promise.resolve(updated?.let(::toWritableMap))
            } catch (error: Exception) {
                promise.reject(
                    "UPDATE_NOTIFICATION_FAILED",
                    "Failed to update notification",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun deleteNotification(
        id: String,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                promise.resolve(repository.delete(id))
            } catch (error: Exception) {
                promise.reject(
                    "DELETE_NOTIFICATION_FAILED",
                    "Failed to delete notification",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun getNotificationApps(promise: Promise) {
        moduleScope.launch {
            try {
                val packageManager = reactContext.packageManager
                val launchableIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val installed = packageManager.queryIntentActivities(
                    launchableIntent,
                    PackageManager.MATCH_ALL
                )

                val observedPackages = repository.getDistinctPackageNames()
                val packageNames = (installed.map { it.activityInfo.packageName } + observedPackages)
                    .toSet()
                    .filter { it != reactContext.packageName }
                    .sorted()

                val apps = packageNames.mapNotNull { packageName ->
                    try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val isSystemApp =
                            appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                                appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

                        NotificationAppInfo(
                            packageName = packageName,
                            appName = appName,
                            isSystemApp = isSystemApp,
                            enabled = filterStore.isPackageEnabled(packageName, isSystemApp),
                            explicitlyConfigured = filterStore.isExplicitlyConfigured(packageName),
                            iconBase64 = applicationIconBase64(appInfo)
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                }

                val result = Arguments.createArray()
                apps.forEach { app -> result.pushMap(toWritableMap(app)) }
                promise.resolve(result)
            } catch (error: Exception) {
                promise.reject(
                    "GET_NOTIFICATION_APPS_FAILED",
                    "Failed to load notification apps",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun setAppNotificationsEnabled(
        packageName: String,
        enabled: Boolean,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                filterStore.setPackageEnabled(packageName, enabled)
                promise.resolve(enabled)
            } catch (error: Exception) {
                promise.reject(
                    "SET_APP_FILTER_FAILED",
                    "Failed to update app notification filter",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun clearAppNotificationOverride(
        packageName: String,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                filterStore.clearPackageOverride(packageName)
                promise.resolve(true)
            } catch (error: Exception) {
                promise.reject(
                    "CLEAR_APP_FILTER_FAILED",
                    "Failed to clear app notification filter override",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun setSystemNotificationsEnabled(
        enabled: Boolean,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                filterStore.setSystemNotificationsEnabled(enabled)
                promise.resolve(enabled)
            } catch (error: Exception) {
                promise.reject(
                    "SET_SYSTEM_FILTER_FAILED",
                    "Failed to update system notification filter",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun getSystemNotificationsEnabled(promise: Promise) {
        moduleScope.launch {
            try {
                promise.resolve(filterStore.areSystemNotificationsEnabled())
            } catch (error: Exception) {
                promise.reject(
                    "GET_SYSTEM_FILTER_FAILED",
                    "Failed to read system notification filter",
                    error
                )
            }
        }
    }

    @ReactMethod
    fun isNotificationListenerEnabled(promise: Promise) {
        try {
            promise.resolve(
                NotificationServiceHealth.isListenerEnabled(reactContext.applicationContext)
            )
        } catch (error: Exception) {
            promise.reject(
                "LISTENER_STATUS_FAILED",
                "Failed to read notification listener status",
                error
            )
        }
    }

    @ReactMethod
    fun isNotificationListenerConnected(promise: Promise) {
        try {
            promise.resolve(
                NotificationServiceHealth.isServiceConnected(reactContext.applicationContext)
            )
        } catch (error: Exception) {
            promise.reject(
                "LISTENER_CONNECTION_STATUS_FAILED",
                "Failed to read notification listener connection status",
                error
            )
        }
    }

    @ReactMethod
    fun getNotificationListenerLastStateChange(promise: Promise) {
        try {
            promise.resolve(
                NotificationServiceHealth.lastStateChange(reactContext.applicationContext)
            )
        } catch (error: Exception) {
            promise.reject(
                "LISTENER_STATE_TIME_FAILED",
                "Failed to read notification listener state timestamp",
                error
            )
        }
    }

    @ReactMethod
    fun openNotificationListenerSettings(promise: Promise) {
        try {
            val activity: Activity = reactContext.currentActivity
                ?: throw IllegalStateException("No active Android activity")
            NotificationServiceHealth.openNotificationListenerSettings(activity)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(
                "OPEN_LISTENER_SETTINGS_FAILED",
                "Failed to open notification listener settings",
                error
            )
        }
    }

    @ReactMethod
    fun isIgnoringBatteryOptimizations(promise: Promise) {
        try {
            promise.resolve(
                NotificationServiceHealth.isIgnoringBatteryOptimizations(reactContext.applicationContext)
            )
        } catch (error: Exception) {
            promise.reject(
                "BATTERY_STATUS_FAILED",
                "Failed to read battery optimization status",
                error
            )
        }
    }

    @ReactMethod
    fun openBatteryOptimizationSettings(promise: Promise) {
        try {
            val activity: Activity = reactContext.currentActivity
                ?: throw IllegalStateException("No active Android activity")
            NotificationServiceHealth.openBatteryOptimizationSettings(activity)
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(
                "OPEN_BATTERY_SETTINGS_FAILED",
                "Failed to open battery optimization settings",
                error
            )
        }
    }

    @ReactMethod
    fun clearAll(promise: Promise) {
        moduleScope.launch {
            try {
                repository.clearAll()
                promise.resolve(true)
            } catch (error: Exception) {
                promise.reject(
                    "CLEAR_NOTIFICATIONS_FAILED",
                    "Failed to clear notifications",
                    error
                )
            }
        }
    }

    /**
     * Exports either the supplied notification IDs or the current filtered query
     * to CSV, XLSX, or JSON and opens the Android share/save chooser.
     */
    @ReactMethod
    fun exportNotifications(
        format: String,
        search: String,
        packageName: String,
        unreadOnly: Boolean,
        activeOnly: Boolean,
        fromTime: Double,
        toTime: Double,
        ids: ReadableArray,
        promise: Promise
    ) {
        moduleScope.launch {
            try {
                val selectedIds = mutableListOf<String>()
                for (index in 0 until ids.size()) {
                    if (!ids.isNull(index)) {
                        ids.getString(index)?.takeIf { it.isNotBlank() }?.let(selectedIds::add)
                    }
                }

                val fileName = exportManager.exportAndShare(
                    format = format,
                    repository = repository,
                    ids = selectedIds,
                    search = search,
                    packageName = packageName,
                    unreadOnly = unreadOnly,
                    activeOnly = activeOnly,
                    fromTime = fromTime.toLong(),
                    toTime = toTime.toLong()
                )
                promise.resolve(fileName)
            } catch (error: Exception) {
                promise.reject(
                    "EXPORT_NOTIFICATIONS_FAILED",
                    "Failed to export notifications",
                    error
                )
            }
        }
    }

    private fun emitNotification(notification: NotificationEntity, eventType: String) {
        if (!reactContext.hasActiveCatalystInstance()) {
            return
        }

        reactContext
            .getJSModule(
                DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
            )
            .emit("notificationLifecycle", toWritableMap(notification, eventType))
    }

    private fun toWritableMap(
        notification: NotificationEntity,
        eventType: String? = null
    ): WritableMap {
        return Arguments.createMap().apply {
            putString("id", notification.id)
            putString("notificationKey", notification.notificationKey)
            putString("packageName", notification.packageName)
            putString("title", notification.title)
            putString("text", notification.text)
            putString("subText", notification.subText)
            putString("category", notification.category)
            putDouble("timestamp", notification.timestamp.toDouble())
            putBoolean("isOngoing", notification.isOngoing)
            putDouble("createdAt", notification.createdAt.toDouble())
            putString("status", notification.status)
            putDouble("updatedAt", notification.updatedAt.toDouble())
            if (notification.removedAt != null) {
                putDouble("removedAt", notification.removedAt.toDouble())
            } else {
                putNull("removedAt")
            }
            putBoolean("isActive", notification.isActive)
            putBoolean("isRead", notification.isRead)
            putString("groupKey", notification.groupKey)
            putBoolean("isGroupSummary", notification.isGroupSummary)
            if (eventType != null) {
                putString("eventType", eventType)
            }
        }
    }

    private fun toWritableMap(app: NotificationAppInfo): WritableMap {
        return Arguments.createMap().apply {
            putString("packageName", app.packageName)
            putString("appName", app.appName)
            putBoolean("isSystemApp", app.isSystemApp)
            putBoolean("enabled", app.enabled)
            putBoolean("explicitlyConfigured", app.explicitlyConfigured)
            if (app.iconBase64 != null) putString("iconBase64", app.iconBase64) else putNull("iconBase64")
        }
    }

    private fun fromReadableMap(data: ReadableMap): NotificationEntity {
        val now = System.currentTimeMillis()

        val notificationKey = data.getString("notificationKey") ?: ""
        require(notificationKey.isNotBlank()) {
            "notificationKey is required"
        }

        return NotificationEntity(
            id = if (data.hasKey("id") && !data.isNull("id")) {
                data.getString("id") ?: ""
            } else {
                UUID.randomUUID().toString()
            },
            notificationKey = notificationKey,
            packageName = data.getString("packageName") ?: "",
            title = data.getString("title") ?: "",
            text = data.getString("text") ?: "",
            subText = data.getString("subText") ?: "",
            category = data.getString("category") ?: "",
            timestamp = if (data.hasKey("timestamp") && !data.isNull("timestamp")) {
                data.getDouble("timestamp").toLong()
            } else {
                now
            },
            isOngoing = if (data.hasKey("isOngoing") && !data.isNull("isOngoing")) {
                data.getBoolean("isOngoing")
            } else {
                false
            },
            status = if (data.hasKey("status") && !data.isNull("status")) {
                data.getString("status") ?: NotificationEntity.STATUS_POSTED
            } else {
                NotificationEntity.STATUS_POSTED
            },
            updatedAt = if (data.hasKey("updatedAt") && !data.isNull("updatedAt")) {
                data.getDouble("updatedAt").toLong()
            } else {
                now
            },
            removedAt = if (data.hasKey("removedAt") && !data.isNull("removedAt")) {
                data.getDouble("removedAt").toLong()
            } else {
                null
            },
            isActive = if (data.hasKey("isActive") && !data.isNull("isActive")) {
                data.getBoolean("isActive")
            } else {
                true
            },
            isRead = if (data.hasKey("isRead") && !data.isNull("isRead")) data.getBoolean("isRead") else false,
            groupKey = if (data.hasKey("groupKey") && !data.isNull("groupKey")) {
                data.getString("groupKey") ?: ""
            } else {
                ""
            },
            isGroupSummary = if (data.hasKey("isGroupSummary") && !data.isNull("isGroupSummary")) {
                data.getBoolean("isGroupSummary")
            } else {
                false
            },
            createdAt = if (data.hasKey("createdAt") && !data.isNull("createdAt")) {
                data.getDouble("createdAt").toLong()
            } else {
                now
            }
        )
    }

    private fun applicationIconBase64(appInfo: ApplicationInfo): String? {
        return try {
            val drawable = reactContext.packageManager.getApplicationIcon(appInfo)
            val width = 96
            val height = 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    override fun invalidate() {
        instance = null
        moduleScope.cancel()
        super.invalidate()
    }
}
