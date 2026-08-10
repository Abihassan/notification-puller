package com.notificationpuller.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["notificationKey"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["packageName"]),
        Index(value = ["status"]),
        Index(value = ["isActive"]),
        Index(value = ["isRead"]),
        Index(value = ["groupKey"])
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val notificationKey: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String,
    val category: String,
    val timestamp: Long,
    val isOngoing: Boolean,
    val createdAt: Long,
    val status: String = STATUS_POSTED,
    val updatedAt: Long = 0L,
    val removedAt: Long? = null,
    val isActive: Boolean = true,
    val isRead: Boolean = false,
    val groupKey: String = "",
    val isGroupSummary: Boolean = false
) {
    companion object {
        const val STATUS_POSTED = "posted"
        const val STATUS_UPDATED = "updated"
        const val STATUS_REMOVED = "removed"
    }
}
