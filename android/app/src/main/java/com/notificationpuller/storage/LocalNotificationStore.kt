package com.notificationpuller.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Simple, dependency-free, file-backed notification store.
 *
 * Notifications are kept inside the app's private files directory, so no
 * storage permission is required and the data is not directly accessible to
 * other applications.
 *
 * All read/write operations are synchronized because the notification
 * listener and the React Native bridge can access this store independently.
 */
class LocalNotificationStore(context: Context) {

    companion object {
        private const val TAG = "NotificationStore"
        private const val FILE_NAME = "notifications.json"
        private const val TEMP_FILE_NAME = "notifications.json.tmp"

        @Volatile
        private var instance: LocalNotificationStore? = null

        fun getInstance(context: Context): LocalNotificationStore {
            return instance ?: synchronized(this) {
                instance ?: LocalNotificationStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val lock = Any()
    private val storageFile = File(context.filesDir, FILE_NAME)
    private val tempFile = File(context.filesDir, TEMP_FILE_NAME)

    fun save(notification: LocalNotification): LocalNotification {
        synchronized(lock) {
            val notifications = readAllLocked().toMutableList()
            val existingIndex = notifications.indexOfFirst {
                it.notificationKey == notification.notificationKey
            }

            val value = if (existingIndex >= 0) {
                notification.copy(
                    id = notifications[existingIndex].id,
                    createdAt = notifications[existingIndex].createdAt
                )
            } else {
                notification.copy(
                    id = notification.id.ifBlank { UUID.randomUUID().toString() },
                    createdAt = if (notification.createdAt > 0) {
                        notification.createdAt
                    } else {
                        System.currentTimeMillis()
                    }
                )
            }

            if (existingIndex >= 0) {
                notifications[existingIndex] = value
            } else {
                notifications.add(value)
            }

            writeAllLocked(notifications)
            return value
        }
    }

    fun update(notification: LocalNotification): LocalNotification? {
        synchronized(lock) {
            val notifications = readAllLocked().toMutableList()
            val index = notifications.indexOfFirst {
                it.id == notification.id
            }

            if (index < 0) {
                return null
            }

            val existing = notifications[index]
            val value = notification.copy(
                id = existing.id,
                createdAt = existing.createdAt
            )

            notifications[index] = value
            writeAllLocked(notifications)
            return value
        }
    }

    fun getById(id: String): LocalNotification? {
        synchronized(lock) {
            return readAllLocked().firstOrNull { it.id == id }
        }
    }

    fun getByNotificationKey(notificationKey: String): LocalNotification? {
        synchronized(lock) {
            return readAllLocked().firstOrNull {
                it.notificationKey == notificationKey
            }
        }
    }

    fun getPage(limit: Int, offset: Int): NotificationPage {
        synchronized(lock) {
            val all = readAllLocked().sortedByDescending { it.timestamp }
            val safeLimit = limit.coerceIn(1, 500)
            val safeOffset = offset.coerceAtLeast(0)

            if (safeOffset >= all.size) {
                return NotificationPage(
                    items = emptyList(),
                    total = all.size,
                    limit = safeLimit,
                    offset = safeOffset,
                    hasMore = false
                )
            }

            val end = minOf(safeOffset + safeLimit, all.size)
            val items = all.subList(safeOffset, end)

            return NotificationPage(
                items = items,
                total = all.size,
                limit = safeLimit,
                offset = safeOffset,
                hasMore = end < all.size
            )
        }
    }

    fun count(): Int {
        synchronized(lock) {
            return readAllLocked().size
        }
    }

    fun delete(id: String): Boolean {
        synchronized(lock) {
            val notifications = readAllLocked().toMutableList()
            val removed = notifications.removeAll { it.id == id }

            if (removed) {
                writeAllLocked(notifications)
            }

            return removed
        }
    }

    fun deleteByNotificationKey(notificationKey: String): Boolean {
        synchronized(lock) {
            val notifications = readAllLocked().toMutableList()
            val removed = notifications.removeAll {
                it.notificationKey == notificationKey
            }

            if (removed) {
                writeAllLocked(notifications)
            }

            return removed
        }
    }

    fun clearAll() {
        synchronized(lock) {
            writeAllLocked(emptyList())
        }
    }

    private fun readAllLocked(): List<LocalNotification> {
        if (!storageFile.exists()) {
            return emptyList()
        }

        return try {
            val content = storageFile.readText(Charsets.UTF_8)
            if (content.isBlank()) {
                emptyList()
            } else {
                val array = JSONArray(content)
                buildList(array.length()) {
                    for (index in 0 until array.length()) {
                        val item = LocalNotification.fromJson(
                            array.getJSONObject(index)
                        )
                        if (item.id.isNotBlank() && item.notificationKey.isNotBlank()) {
                            add(item)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to read local notification storage", error)
            emptyList()
        }
    }

    private fun writeAllLocked(notifications: List<LocalNotification>) {
        try {
            val array = JSONArray()
            notifications.forEach { array.put(it.toJson()) }

            tempFile.writeText(
                array.toString(),
                Charsets.UTF_8
            )

            if (storageFile.exists() && !storageFile.delete()) {
                throw IllegalStateException("Unable to replace notification storage file")
            }

            if (!tempFile.renameTo(storageFile)) {
                throw IllegalStateException("Unable to commit notification storage file")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to write local notification storage", error)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw error
        }
    }
}

data class NotificationPage(
    val items: List<LocalNotification>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)
