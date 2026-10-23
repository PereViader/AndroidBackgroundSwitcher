package com.pereviader.backgroundswitcher.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaper_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val INTERVAL_MINUTES = longPreferencesKey("interval_minutes")
        val TARGET_SCREEN = stringPreferencesKey("target_screen")
        val ROTATION_ORDER = stringPreferencesKey("rotation_order")
        val ONLY_ON_WIFI = booleanPreferencesKey("only_on_wifi")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val LAST_ROTATED_AT = longPreferencesKey("last_rotated_at")
        val CURRENT_WALLPAPER_URI = stringPreferencesKey("current_wallpaper_uri")
        val CURRENT_PHOTO_ID = stringPreferencesKey("current_photo_id")
    }

    val settingsFlow: Flow<WallpaperSettings> = context.dataStore.data.map { preferences ->
        WallpaperSettings(
            intervalMinutes = preferences[Keys.INTERVAL_MINUTES] ?: 60L,
            targetScreen = preferences[Keys.TARGET_SCREEN]?.let {
                runCatching { TargetScreen.valueOf(it) }.getOrDefault(TargetScreen.BOTH)
            } ?: TargetScreen.BOTH,
            rotationOrder = preferences[Keys.ROTATION_ORDER]?.let {
                runCatching { RotationOrder.valueOf(it) }.getOrDefault(RotationOrder.LEAST_RECENTLY_USED)
            } ?: RotationOrder.LEAST_RECENTLY_USED,
            onlyOnWifi = preferences[Keys.ONLY_ON_WIFI] ?: true,
            isEnabled = preferences[Keys.IS_ENABLED] ?: true,
            lastRotatedAt = preferences[Keys.LAST_ROTATED_AT] ?: 0L,
            currentWallpaperUri = preferences[Keys.CURRENT_WALLPAPER_URI],
            currentPhotoId = preferences[Keys.CURRENT_PHOTO_ID]
        )
    }

    suspend fun updateInterval(minutes: Long) {
        context.dataStore.edit { it[Keys.INTERVAL_MINUTES] = minutes }
    }

    suspend fun updateTargetScreen(target: TargetScreen) {
        context.dataStore.edit { it[Keys.TARGET_SCREEN] = target.name }
    }

    suspend fun updateRotationOrder(order: RotationOrder) {
        context.dataStore.edit { it[Keys.ROTATION_ORDER] = order.name }
    }

    suspend fun updateOnlyOnWifi(onlyWifi: Boolean) {
        context.dataStore.edit { it[Keys.ONLY_ON_WIFI] = onlyWifi }
    }

    suspend fun updateIsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun updateLastRotated(timestamp: Long, uri: String?, photoId: String? = null) {
        context.dataStore.edit {
            it[Keys.LAST_ROTATED_AT] = timestamp
            if (uri != null) it[Keys.CURRENT_WALLPAPER_URI] = uri
            if (photoId != null) it[Keys.CURRENT_PHOTO_ID] = photoId
        }
    }
}
