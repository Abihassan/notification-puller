package com.notificationpuller.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.util.UUID

@Dao
interface NotificationDao {

    @Query("""
        SELECT * FROM notifications
        WHERE (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE
            OR text LIKE '%' || :search || '%' COLLATE NOCASE
            OR subText LIKE '%' || :search || '%' COLLATE NOCASE
            OR packageName LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:packageName = '' OR packageName = :packageName)
          AND (:unreadOnly = 0 OR isRead = 0)
          AND (:activeOnly = 0 OR isActive = 1)
          AND (:fromTime = 0 OR timestamp >= :fromTime)
          AND (:toTime = 0 OR timestamp <= :toTime)
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getNotifications(
        limit: Int,
        offset: Int,
        search: String,
        packageName: String,
        unreadOnly: Boolean,
        activeOnly: Boolean,
        fromTime: Long,
        toTime: Long
    ): List<NotificationEntity>

    @Query("""
        SELECT COUNT(*) FROM notifications
        WHERE (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE
            OR text LIKE '%' || :search || '%' COLLATE NOCASE
            OR subText LIKE '%' || :search || '%' COLLATE NOCASE
            OR packageName LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:packageName = '' OR packageName = :packageName)
          AND (:unreadOnly = 0 OR isRead = 0)
          AND (:activeOnly = 0 OR isActive = 1)
          AND (:fromTime = 0 OR timestamp >= :fromTime)
          AND (:toTime = 0 OR timestamp <= :toTime)
    """)
    suspend fun getFilteredCount(
        search: String,
        packageName: String,
        unreadOnly: Boolean,
        activeOnly: Boolean,
        fromTime: Long,
        toTime: Long
    ): Int

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE id IN (:ids) ORDER BY timestamp DESC, id DESC")
    suspend fun getByIds(ids: List<String>): List<NotificationEntity>

    @Query("""
        SELECT * FROM notifications
        WHERE (:search = '' OR title LIKE '%' || :search || '%' COLLATE NOCASE
            OR text LIKE '%' || :search || '%' COLLATE NOCASE
            OR subText LIKE '%' || :search || '%' COLLATE NOCASE
            OR packageName LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:packageName = '' OR packageName = :packageName)
          AND (:unreadOnly = 0 OR isRead = 0)
          AND (:activeOnly = 0 OR isActive = 1)
          AND (:fromTime = 0 OR timestamp >= :fromTime)
          AND (:toTime = 0 OR timestamp <= :toTime)
        ORDER BY timestamp DESC, id DESC
    """)
    suspend fun getAllFiltered(
        search: String,
        packageName: String,
        unreadOnly: Boolean,
        activeOnly: Boolean,
        fromTime: Long,
        toTime: Long
    ): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE notificationKey = :notificationKey LIMIT 1")
    suspend fun getByNotificationKey(notificationKey: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("SELECT DISTINCT packageName FROM notifications ORDER BY packageName ASC")
    suspend fun getDistinctPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(notification: NotificationEntity)

    @Update
    suspend fun update(notification: NotificationEntity): Int

    @Query("UPDATE notifications SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean): Int

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM notifications")
    suspend fun clearAll(): Int

    @Transaction
    suspend fun upsertByNotificationKey(notification: NotificationEntity): NotificationEntity {
        val now = System.currentTimeMillis()
        val existing = getByNotificationKey(notification.notificationKey)

        return if (existing == null) {
            val value = notification.copy(
                id = notification.id.ifBlank { UUID.randomUUID().toString() },
                createdAt = notification.createdAt.takeIf { it > 0 } ?: now,
                status = NotificationEntity.STATUS_POSTED,
                updatedAt = now,
                removedAt = null,
                isActive = true,
                isRead = false
            )
            insert(value)
            value
        } else {
            val contentChanged =
                existing.packageName != notification.packageName ||
                existing.title != notification.title ||
                existing.text != notification.text ||
                existing.subText != notification.subText ||
                existing.category != notification.category ||
                existing.timestamp != notification.timestamp ||
                existing.isOngoing != notification.isOngoing ||
                existing.groupKey != notification.groupKey ||
                existing.isGroupSummary != notification.isGroupSummary

            val value = notification.copy(
                id = existing.id,
                createdAt = existing.createdAt,
                status = if (contentChanged) NotificationEntity.STATUS_UPDATED else
                    existing.status.takeUnless { it == NotificationEntity.STATUS_REMOVED }
                        ?: NotificationEntity.STATUS_POSTED,
                updatedAt = if (contentChanged) now else existing.updatedAt,
                removedAt = null,
                isActive = true,
                isRead = if (contentChanged) false else existing.isRead
            )
            update(value)
            value
        }
    }

    @Transaction
    suspend fun markRemovedByNotificationKey(
        notificationKey: String,
        removedAt: Long
    ): NotificationEntity? {
        val existing = getByNotificationKey(notificationKey) ?: return null
        val value = existing.copy(
            status = NotificationEntity.STATUS_REMOVED,
            updatedAt = removedAt,
            removedAt = removedAt,
            isActive = false
        )
        update(value)
        return value
    }

}
