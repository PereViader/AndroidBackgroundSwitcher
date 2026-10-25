package com.pereviader.backgroundswitcher.domain.coordinator

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import org.junit.Before
import org.junit.Test

class WallpaperCycleCoordinatorTest {

    private lateinit var coordinator: WallpaperCycleCoordinator
    private val defaultSettings = WallpaperSettings(
        isEnabled = true,
        cycleOnUnlock = true,
        doubleTapToCycle = true,
        intervalMinutes = 15L
    )

    private val samplePhoto1 = PhotoItem(
        id = "photo_1",
        albumId = "album_1",
        uri = "content://media/1",
        cropX = 0.5f,
        cropY = 0.5f,
        zoom = 1.0f
    )

    private val samplePhoto2 = PhotoItem(
        id = "photo_2",
        albumId = "album_1",
        uri = "content://media/2",
        cropX = 0.5f,
        cropY = 0.5f,
        zoom = 1.0f
    )

    @Before
    fun setUp() {
        coordinator = WallpaperCycleCoordinator()
    }

    @Test
    fun `initial load triggers transition without animation when activePhoto is null`() {
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.InitialLoad(samplePhoto1),
            defaultSettings,
            nowMillis = 1000L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        val transition = decision as WallpaperCycleCoordinator.CycleDecision.TransitionTo
        assertThat(transition.targetPhoto).isEqualTo(samplePhoto1)
        assertThat(transition.animate).isFalse()
        assertThat(transition.reason).isEqualTo("initial_load")
    }

    @Test
    fun `screen unlock triggers request next photo when enabled and surface visible`() {
        coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = true),
            defaultSettings,
            nowMillis = 1000L
        )
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            defaultSettings,
            nowMillis = 2000L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        val request = decision as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto
        assertThat(request.reason).isEqualTo("user_present")
        assertThat(request.animate).isTrue()
    }

    @Test
    fun `screen unlock is ignored when cycleOnUnlock is false`() {
        val disabledUnlockSettings = defaultSettings.copy(cycleOnUnlock = false)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            disabledUnlockSettings,
            nowMillis = 2000L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
        assertThat((decision as WallpaperCycleCoordinator.CycleDecision.Ignored).reason).isEqualTo("unlock_cycling_disabled")
    }

    @Test
    fun `screen unlock triggers request next photo even when automatic cycling isEnabled is false`() {
        coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = true),
            defaultSettings,
            nowMillis = 1000L
        )
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val disabledTimerSettings = defaultSettings.copy(isEnabled = false, cycleOnUnlock = true)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            disabledTimerSettings,
            nowMillis = 2000L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        val request = decision as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto
        assertThat(request.reason).isEqualTo("user_present")
        assertThat(request.animate).isTrue()
    }

    @Test
    fun `screen unlock is debounced if subsequent unlock occurs within debounce window`() {
        coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = true),
            defaultSettings,
            nowMillis = 1000L
        )
        // First unlock at 1000ms
        val decision1 = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            defaultSettings,
            nowMillis = 1000L
        )
        assertThat(decision1).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)

        // Second unlock at 2000ms (within 3000ms debounce)
        val decision2 = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            defaultSettings,
            nowMillis = 2000L
        )
        assertThat(decision2).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
        assertThat((decision2 as WallpaperCycleCoordinator.CycleDecision.Ignored).reason).isEqualTo("unlock_debounced")

        // Third unlock at 4500ms (after debounce)
        val decision3 = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = true),
            defaultSettings,
            nowMillis = 4500L
        )
        assertThat(decision3).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
    }

    @Test
    fun `screen unlock when surface invisible marks pendingUnlockCycle and triggers once surface becomes visible`() {
        // Unlock occurs while phone surface is not yet visible (lock screen / screen turning on)
        val decision1 = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = false),
            defaultSettings,
            nowMillis = 1000L
        )
        assertThat(decision1).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
        assertThat(coordinator.pendingUnlockCycle).isTrue()

        // Surface now becomes visible
        val decision2 = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = true),
            defaultSettings,
            nowMillis = 1100L
        )
        assertThat(decision2).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        assertThat((decision2 as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto).reason).isEqualTo("pending_unlock")
        assertThat(coordinator.pendingUnlockCycle).isFalse()
    }

    @Test
    fun `timer fired triggers request next photo if surface visible`() {
        coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = true),
            defaultSettings,
            nowMillis = 1000L
        )

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.TimerFired,
            defaultSettings,
            nowMillis = 2000L
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        assertThat((decision as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto).reason).isEqualTo("timer")
    }

    @Test
    fun `timer fired schedules next timer if surface invisible`() {
        coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(isVisible = false),
            defaultSettings,
            nowMillis = 1000L
        )

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.TimerFired,
            defaultSettings,
            nowMillis = 2000L
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.ScheduleNextTimer::class.java)
        assertThat((decision as WallpaperCycleCoordinator.CycleDecision.ScheduleNextTimer).delayMillis).isEqualTo(defaultSettings.intervalMillis)
    }

    @Test
    fun `double tap triggers request next photo when doubleTapToCycle is true`() {
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 1000L
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        assertThat((decision as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto).reason).isEqualTo("double_tap")
    }

    @Test
    fun `double tap triggers request next photo even when automatic cycling isEnabled is false`() {
        val disabledSettings = defaultSettings.copy(isEnabled = false, doubleTapToCycle = true)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            disabledSettings,
            nowMillis = 1000L
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        assertThat((decision as WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto).reason).isEqualTo("double_tap")
    }

    @Test
    fun `double tap is ignored when doubleTapToCycle is false`() {
        val noDoubleTapSettings = defaultSettings.copy(doubleTapToCycle = false)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            noDoubleTapSettings,
            nowMillis = 1000L
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
    }

    @Test
    fun `double tap skips if already cycling`() {
        coordinator.onTransitionStarted(samplePhoto1)

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 1000L
        )
        assertThat(decision).isEqualTo(WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling)
    }

    @Test
    fun `crop change triggers immediate non-animated transition for active photo`() {
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val modifiedPhoto = samplePhoto1.copy(cropX = 0.8f, zoom = 1.5f)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.CropChanged(modifiedPhoto),
            defaultSettings,
            nowMillis = 1200L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        val transition = decision as WallpaperCycleCoordinator.CycleDecision.TransitionTo
        assertThat(transition.targetPhoto).isEqualTo(modifiedPhoto)
        assertThat(transition.animate).isFalse()
        assertThat(transition.reason).isEqualTo("crop_updated")
    }

    @Test
    fun `crop change ignored for non-active photo`() {
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val differentPhoto = samplePhoto2.copy(cropX = 0.8f)
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.CropChanged(differentPhoto),
            defaultSettings,
            nowMillis = 1200L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
    }

    @Test
    fun `external photo change triggers animated transition for different photo`() {
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ExternalPhotoChanged(samplePhoto2),
            defaultSettings,
            nowMillis = 1200L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        val transition = decision as WallpaperCycleCoordinator.CycleDecision.TransitionTo
        assertThat(transition.targetPhoto).isEqualTo(samplePhoto2)
        assertThat(transition.animate).isTrue()
        assertThat(transition.reason).isEqualTo("external_selection")
    }

    @Test
    fun `external photo change overrides in-flight transition to a different photo`() {
        coordinator.onTransitionStarted(samplePhoto1)

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ExternalPhotoChanged(samplePhoto2),
            defaultSettings,
            nowMillis = 1200L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        val transition = decision as WallpaperCycleCoordinator.CycleDecision.TransitionTo
        assertThat(transition.targetPhoto).isEqualTo(samplePhoto2)
        assertThat(coordinator.targetPhoto).isEqualTo(samplePhoto2)
    }

    @Test
    fun `manual selection always triggers transition and updates targetPhoto`() {
        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 1000L)

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ManualSelection(samplePhoto2),
            defaultSettings,
            nowMillis = 1200L
        )

        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        val transition = decision as WallpaperCycleCoordinator.CycleDecision.TransitionTo
        assertThat(transition.targetPhoto).isEqualTo(samplePhoto2)
    }

    @Test
    fun `transition lifecycle updates coordinator state correctly`() {
        assertThat(coordinator.isCycling).isFalse()
        assertThat(coordinator.activePhoto).isNull()
        assertThat(coordinator.targetPhoto).isNull()

        coordinator.onTransitionStarted(samplePhoto1)
        assertThat(coordinator.isCycling).isTrue()
        assertThat(coordinator.targetPhoto).isEqualTo(samplePhoto1)

        coordinator.onTransitionCompleted(samplePhoto1, nowMillis = 5000L)
        assertThat(coordinator.isCycling).isFalse()
        assertThat(coordinator.activePhoto).isEqualTo(samplePhoto1)
        assertThat(coordinator.targetPhoto).isNull()
        assertThat(coordinator.lastCycleTime).isEqualTo(5000L)

        // Starting another transition then failing
        coordinator.onTransitionStarted(samplePhoto2)
        assertThat(coordinator.isCycling).isTrue()
        coordinator.onTransitionFailed()
        assertThat(coordinator.isCycling).isFalse()
        assertThat(coordinator.targetPhoto).isNull()
        // Active photo remains previous
        assertThat(coordinator.activePhoto).isEqualTo(samplePhoto1)
    }
}
