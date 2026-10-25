package com.pereviader.backgroundswitcher.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesManagerTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createPreferencesManager(): PreferencesManager {
        val testFile = tmpFolder.newFile("test_wallpaper_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        return PreferencesManager(dataStore)
    }

    @Test
    fun `settingsFlow maps default cycleOnUnlock as false`() = runTest {
        val preferencesManager = createPreferencesManager()
        val settings = preferencesManager.settingsFlow.first()
        assertThat(settings.cycleOnUnlock).isFalse()
    }

    @Test
    fun `settingsFlow maps default doubleTapToCycle as true`() = runTest {
        val preferencesManager = createPreferencesManager()
        val settings = preferencesManager.settingsFlow.first()
        assertThat(settings.doubleTapToCycle).isTrue()
    }

    @Test
    fun `updateCycleOnUnlock updates cycleOnUnlock in preferences mapping`() = runTest {
        val preferencesManager = createPreferencesManager()

        preferencesManager.updateCycleOnUnlock(true)
        val updated = preferencesManager.settingsFlow.first()
        assertThat(updated.cycleOnUnlock).isTrue()

        preferencesManager.updateCycleOnUnlock(false)
        val toggledBack = preferencesManager.settingsFlow.first()
        assertThat(toggledBack.cycleOnUnlock).isFalse()
    }

    @Test
    fun `updateDoubleTapToCycle updates doubleTapToCycle in preferences mapping`() = runTest {
        val preferencesManager = createPreferencesManager()

        preferencesManager.updateDoubleTapToCycle(false)
        val updated = preferencesManager.settingsFlow.first()
        assertThat(updated.doubleTapToCycle).isFalse()

        preferencesManager.updateDoubleTapToCycle(true)
        val toggledBack = preferencesManager.settingsFlow.first()
        assertThat(toggledBack.doubleTapToCycle).isTrue()
    }

    @Test
    fun `all preferences mapped correctly including cycleOnUnlock and doubleTapToCycle`() = runTest {
        val preferencesManager = createPreferencesManager()

        preferencesManager.updateInterval(45L)
        preferencesManager.updateTargetScreen(TargetScreen.LOCK)
        preferencesManager.updateRotationOrder(RotationOrder.CHRONOLOGICAL)
        preferencesManager.updateOnlyOnWifi(false)
        preferencesManager.updateIsEnabled(false)
        preferencesManager.updateCycleOnUnlock(true)
        preferencesManager.updateDoubleTapToCycle(false)
        preferencesManager.updateLastRotated(123456789L, "content://test/uri", "photo_99")

        val settings = preferencesManager.settingsFlow.first()
        assertThat(settings.intervalMinutes).isEqualTo(45L)
        assertThat(settings.intervalSeconds).isEqualTo(45L * 60L)
        assertThat(settings.targetScreen).isEqualTo(TargetScreen.LOCK)
        assertThat(settings.rotationOrder).isEqualTo(RotationOrder.CHRONOLOGICAL)
        assertThat(settings.onlyOnWifi).isFalse()
        assertThat(settings.isEnabled).isFalse()
        assertThat(settings.cycleOnUnlock).isTrue()
        assertThat(settings.doubleTapToCycle).isFalse()
        assertThat(settings.lastRotatedAt).isEqualTo(123456789L)
        assertThat(settings.currentWallpaperUri).isEqualTo("content://test/uri")
        assertThat(settings.currentPhotoId).isEqualTo("photo_99")
    }

    @Test
    fun `updateIntervalSeconds sets sub-minute interval for fast testing`() = runTest {
        val preferencesManager = createPreferencesManager()
        preferencesManager.updateIntervalSeconds(10L)

        val settings = preferencesManager.settingsFlow.first()
        assertThat(settings.intervalSeconds).isEqualTo(10L)
        assertThat(settings.intervalMillis).isEqualTo(10000L)
    }

    @Test
    fun `separateScreensMode, fallbackListIds, and lockRotation state map and update properly`() = runTest {
        val preferencesManager = createPreferencesManager()

        preferencesManager.updateIsSeparateScreensMode(true)
        preferencesManager.updateFallbackListId(ScheduleScreenScope.SHARED, "list_shared")
        preferencesManager.updateFallbackListId(ScheduleScreenScope.HOME, "list_home")
        preferencesManager.updateFallbackListId(ScheduleScreenScope.LOCK, "list_lock")
        preferencesManager.updateLastLockRotated(987654321L, "content://lock/uri", "lock_photo_42")

        val settings = preferencesManager.settingsFlow.first()
        assertThat(settings.isSeparateScreensMode).isTrue()
        assertThat(settings.fallbackListIdShared).isEqualTo("list_shared")
        assertThat(settings.fallbackListIdHome).isEqualTo("list_home")
        assertThat(settings.fallbackListIdLock).isEqualTo("list_lock")
        assertThat(settings.currentLockWallpaperUri).isEqualTo("content://lock/uri")
        assertThat(settings.currentLockPhotoId).isEqualTo("lock_photo_42")
    }
}
