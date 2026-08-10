package com.notificationpuller.database

import android.content.Context

/**
 * Local-only repository for notification persistence.
 *
 * Room is the on-device database. No backend, account, or cloud service is used.
 */
class NotificationRepository private constructor(
    context: Context
) {

    companion object {
        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val dao = NotificationDatabase
        .getInstance(context)
        .notificationDao()

    suspend fun save(notification: NotificationEntity): NotificationEntity =
        dao.upsertByNotificationKey(notification)

    suspend fun update(notification: NotificationEntity): NotificationEntity? {
        val existing = dao.getById(notification.id) ?: return null
        val value = notification.copy(
            id = existing.id,
            createdAt = existing.createdAt,
            updatedAt = if (notification.updatedAt > 0) {
                notification.updatedAt
            } else {
                System.currentTimeMillis()
            }
        )
        dao.update(value)
        return value
    }

    suspend fun markRemoved(
        notificationKey: String,
        removedAt: Long = System.currentTimeMillis()
    ): NotificationEntity? =
        dao.markRemovedByNotificationKey(notificationKey, removedAt)

    suspend fun getById(id: String): NotificationEntity? = dao.getById(id)

    suspend fun getByIds(ids: List<String>): List<NotificationEntity> =
        if (ids.isEmpty()) emptyList() else dao.getByIds(ids)

    suspend fun getAllFiltered(
        search: String = "",
        packageName: String = "",
        unreadOnly: Boolean = false,
        activeOnly: Boolean = false,
        fromTime: Long = 0L,
        toTime: Long = 0L
    ): List<NotificationEntity> = dao.getAllFiltered(
        search.trim(), packageName, unreadOnly, activeOnly, fromTime, toTime
    )

    suspend fun getByNotificationKey(notificationKey: String): NotificationEntity? =
        dao.getByNotificationKey(notificationKey)

    suspend fun getPage(
        limit: Int,
        offset: Int,
        search: String = "",
        packageName: String = "",
        unreadOnly: Boolean = false,
        activeOnly: Boolean = false,
        fromTime: Long = 0L,
        toTime: Long = 0L
    ): NotificationPage {
        val safeLimit = limit.coerceIn(1, 500)
        val safeOffset = offset.coerceAtLeast(0)
        val normalizedSearch = search.trim()
        val total = dao.getFilteredCount(normalizedSearch, packageName, unreadOnly, activeOnly, fromTime, toTime)
        val items = if (safeOffset >= total) emptyList() else dao.getNotifications(
            safeLimit, safeOffset, normalizedSearch, packageName, unreadOnly, activeOnly, fromTime, toTime
        )
        return NotificationPage(items, total, safeLimit, safeOffset, safeOffset + items.size < total)
    }

    suspend fun count(): Int = dao.getCount()

    suspend fun unreadCount(): Int = dao.getUnreadCount()

    suspend fun setRead(id: String, isRead: Boolean): Boolean = dao.setRead(id, isRead) > 0

    suspend fun getDistinctPackageNames(): List<String> = dao.getDistinctPackageNames()

    suspend fun delete(id: String): Boolean = dao.deleteById(id) > 0

    suspend fun deleteByNotificationKey(notificationKey: String): Boolean {
        val existing = dao.getByNotificationKey(notificationKey) ?: return false
        return dao.deleteById(existing.id) > 0
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}

data class NotificationPage(
    val items: List<NotificationEntity>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)
