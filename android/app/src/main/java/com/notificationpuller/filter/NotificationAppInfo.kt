package com.notificationpuller.filter

data class NotificationAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val enabled: Boolean,
    val explicitlyConfigured: Boolean,
    val iconBase64: String? = null
)
