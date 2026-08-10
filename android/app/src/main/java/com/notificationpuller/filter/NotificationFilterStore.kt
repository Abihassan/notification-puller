package com.notificationpuller.filter

import android.content.Context
import android.content.SharedPreferences

/**
 * Local per-app notification collection settings.
 *
 * Apps are enabled by default. Only explicit overrides are persisted.
 * This keeps the settings small even when many apps are installed.
 */
class NotificationFilterStore private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "notification_filters"
        private const val KEY_DISABLED_PACKAGES = "disabled_packages"
        private const val KEY_ENABLED_OVERRIDES = "enabled_overrides"
        private const val KEY_SYSTEM_ENABLED = "system_notifications_enabled"

        @Volatile
        private var instance: NotificationFilterStore? = null

        fun getInstance(context: Context): NotificationFilterStore {
            return instance ?: synchronized(this) {
                instance ?: NotificationFilterStore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPackageEnabled(packageName: String, isSystemApp: Boolean): Boolean {
        val disabled = preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()) ?: emptySet()
        if (packageName in disabled) return false

        val enabledOverrides =
            preferences.getStringSet(KEY_ENABLED_OVERRIDES, emptySet()) ?: emptySet()
        if (packageName in enabledOverrides) return true

        return if (isSystemApp) {
            preferences.getBoolean(KEY_SYSTEM_ENABLED, true)
        } else {
            true
        }
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        require(packageName.isNotBlank()) { "packageName is required" }

        val disabled = (
            preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()) ?: emptySet()
        ).toMutableSet()
        val enabledOverrides = (
            preferences.getStringSet(KEY_ENABLED_OVERRIDES, emptySet()) ?: emptySet()
        ).toMutableSet()

        disabled.remove(packageName)
        enabledOverrides.remove(packageName)

        if (enabled) {
            enabledOverrides.add(packageName)
        } else {
            disabled.add(packageName)
        }

        preferences.edit()
            .putStringSet(KEY_DISABLED_PACKAGES, disabled)
            .putStringSet(KEY_ENABLED_OVERRIDES, enabledOverrides)
            .apply()
    }

    fun setSystemNotificationsEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SYSTEM_ENABLED, enabled)
            .apply()
    }

    fun areSystemNotificationsEnabled(): Boolean =
        preferences.getBoolean(KEY_SYSTEM_ENABLED, true)

    fun isExplicitlyConfigured(packageName: String): Boolean {
        val disabled = preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()) ?: emptySet()
        val enabled = preferences.getStringSet(KEY_ENABLED_OVERRIDES, emptySet()) ?: emptySet()
        return packageName in disabled || packageName in enabled
    }

    fun getDisabledPackages(): Set<String> =
        preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()) ?: emptySet()

    fun getEnabledOverrides(): Set<String> =
        preferences.getStringSet(KEY_ENABLED_OVERRIDES, emptySet()) ?: emptySet()

    fun clearPackageOverride(packageName: String) {
        val disabled = (
            preferences.getStringSet(KEY_DISABLED_PACKAGES, emptySet()) ?: emptySet()
        ).toMutableSet()
        val enabled = (
            preferences.getStringSet(KEY_ENABLED_OVERRIDES, emptySet()) ?: emptySet()
        ).toMutableSet()

        disabled.remove(packageName)
        enabled.remove(packageName)

        preferences.edit()
            .putStringSet(KEY_DISABLED_PACKAGES, disabled)
            .putStringSet(KEY_ENABLED_OVERRIDES, enabled)
            .apply()
    }
}
