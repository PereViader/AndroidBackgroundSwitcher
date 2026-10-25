package com.pereviader.backgroundswitcher.domain.coordinator

import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings

/**
 * Pure Kotlin coordinator that encapsulates state machine logic and decision-making
 * for wallpaper transitions, debouncing, unlock triggers, and timer scheduling.
 *
 * This class has zero Android framework dependencies, making it 100% testable in pure JVM unit tests.
 */
class WallpaperCycleCoordinator(
    val unlockDebounceMillis: Long = DEFAULT_UNLOCK_DEBOUNCE_MILLIS,
    val minCycleIntervalMillis: Long = DEFAULT_MIN_CYCLE_INTERVAL_MILLIS
) {
    companion object {
        const val DEFAULT_UNLOCK_DEBOUNCE_MILLIS = 3000L
        const val DEFAULT_MIN_CYCLE_INTERVAL_MILLIS = 1000L
    }

    sealed interface CycleTrigger {
        data class ScreenUnlocked(val isSurfaceVisible: Boolean) : CycleTrigger
        data class ScreenTurnedOn(val nowMillis: Long) : CycleTrigger
        data object TimerFired : CycleTrigger
        data object DoubleTapped : CycleTrigger
        data class ManualSelection(val photo: PhotoItem) : CycleTrigger
        data class CropChanged(val photo: PhotoItem) : CycleTrigger
        data class ExternalPhotoChanged(val photo: PhotoItem) : CycleTrigger
        data class SurfaceVisibilityChanged(val isVisible: Boolean) : CycleTrigger
        data class InitialLoad(val candidatePhoto: PhotoItem?) : CycleTrigger
    }

    sealed interface CycleDecision {
        data class TransitionTo(
            val targetPhoto: PhotoItem,
            val reason: String,
            val animate: Boolean
        ) : CycleDecision

        data class RequestNextPhoto(
            val reason: String,
            val animate: Boolean
        ) : CycleDecision

        data class ScheduleNextTimer(val delayMillis: Long) : CycleDecision

        data object SkipAlreadyCycling : CycleDecision

        data class Ignored(val reason: String) : CycleDecision
    }

    var activePhoto: PhotoItem? = null
        private set

    var targetPhoto: PhotoItem? = null
        private set

    var isCycling: Boolean = false
        private set

    var isSurfaceVisible: Boolean = false
        private set

    var lastCycleTime: Long = 0L
        private set

    var lastUnlockCycleTime: Long = 0L
        private set

    var pendingUnlockCycle: Boolean = false
        private set

    fun evaluateTrigger(
        trigger: CycleTrigger,
        settings: WallpaperSettings,
        nowMillis: Long
    ): CycleDecision {
        when (trigger) {
            is CycleTrigger.SurfaceVisibilityChanged -> {
                isSurfaceVisible = trigger.isVisible
                if (!trigger.isVisible) {
                    return CycleDecision.Ignored("surface_hidden")
                }
                if (pendingUnlockCycle && settings.cycleOnUnlock) {
                    if (!isCycling) {
                        pendingUnlockCycle = false
                        return CycleDecision.RequestNextPhoto(reason = "pending_unlock", animate = (activePhoto != null))
                    }
                }
                return CycleDecision.Ignored("surface_shown_no_pending_work")
            }

            is CycleTrigger.InitialLoad -> {
                if (activePhoto == null && trigger.candidatePhoto != null) {
                    return CycleDecision.TransitionTo(
                        targetPhoto = trigger.candidatePhoto,
                        reason = "initial_load",
                        animate = false
                    )
                }
                return CycleDecision.Ignored("initial_load_not_needed")
            }

            is CycleTrigger.ScreenUnlocked -> {
                if (!settings.cycleOnUnlock) {
                    return CycleDecision.Ignored("unlock_cycling_disabled")
                }
                if (lastUnlockCycleTime > 0L && (nowMillis - lastUnlockCycleTime < unlockDebounceMillis)) {
                    return CycleDecision.Ignored("unlock_debounced")
                }
                lastUnlockCycleTime = nowMillis

                if (!trigger.isSurfaceVisible && !isSurfaceVisible) {
                    pendingUnlockCycle = true
                    return CycleDecision.Ignored("unlock_queued_surface_invisible")
                }

                if (isCycling) {
                    pendingUnlockCycle = true
                    return CycleDecision.SkipAlreadyCycling
                }

                return CycleDecision.RequestNextPhoto(reason = "user_present", animate = (activePhoto != null))
            }

            is CycleTrigger.ScreenTurnedOn -> {
                if (!settings.isEnabled) {
                    return CycleDecision.Ignored("disabled")
                }
                val intervalMillis = settings.intervalMillis
                val intervalElapsed = (lastCycleTime > 0L) && (nowMillis - lastCycleTime >= intervalMillis)
                if (intervalElapsed) {
                    if (isCycling) return CycleDecision.SkipAlreadyCycling
                    return CycleDecision.RequestNextPhoto(reason = "screen_on_interval", animate = (activePhoto != null))
                }
                return CycleDecision.Ignored("screen_on_interval_not_elapsed")
            }

            is CycleTrigger.TimerFired -> {
                if (!settings.isEnabled) {
                    return CycleDecision.Ignored("disabled")
                }
                if (!isSurfaceVisible) {
                    return CycleDecision.ScheduleNextTimer(settings.intervalMillis)
                }
                if (isCycling) {
                    return CycleDecision.SkipAlreadyCycling
                }
                return CycleDecision.RequestNextPhoto(reason = "timer", animate = (activePhoto != null))
            }

            is CycleTrigger.DoubleTapped -> {
                if (!settings.doubleTapToCycle) {
                    return CycleDecision.Ignored("double_tap_disabled")
                }
                if (isCycling) {
                    return CycleDecision.SkipAlreadyCycling
                }
                return CycleDecision.RequestNextPhoto(reason = "double_tap", animate = (activePhoto != null))
            }

            is CycleTrigger.ManualSelection -> {
                if (trigger.photo.id == activePhoto?.id && !isCropOrFramingChanged(trigger.photo)) {
                    return CycleDecision.Ignored("already_active")
                }
                if (isCycling) {
                    // Manual selection takes priority over normal rotation
                    targetPhoto = trigger.photo
                }
                return CycleDecision.TransitionTo(
                    targetPhoto = trigger.photo,
                    reason = "manual_selection",
                    animate = (activePhoto != null)
                )
            }

            is CycleTrigger.CropChanged -> {
                val photo = trigger.photo
                val isActive = photo.id == activePhoto?.id
                if (isActive && isCropOrFramingChanged(photo)) {
                    return CycleDecision.TransitionTo(
                        targetPhoto = photo,
                        reason = "crop_updated",
                        animate = false
                    )
                }
                return CycleDecision.Ignored("crop_unchanged_or_not_active")
            }

            is CycleTrigger.ExternalPhotoChanged -> {
                val photo = trigger.photo
                val referencePhoto = if (isCycling && targetPhoto != null) targetPhoto else activePhoto
                val isDifferentPhoto = photo.id != activePhoto?.id && photo.id != targetPhoto?.id
                val isCropChanged = photo.id == referencePhoto?.id && isCropOrFramingChanged(photo, referencePhoto)

                if (isDifferentPhoto || isCropChanged) {
                    if (isCycling) {
                        targetPhoto = photo
                    }
                    return CycleDecision.TransitionTo(
                        targetPhoto = photo,
                        reason = if (isCropChanged) "crop_updated" else "external_selection",
                        animate = (activePhoto != null && isDifferentPhoto)
                    )
                }
                return CycleDecision.Ignored("external_photo_unchanged_or_cycling")
            }
        }
    }

    fun onTransitionStarted(target: PhotoItem? = null) {
        isCycling = true
        targetPhoto = target
    }

    fun onTransitionCompleted(photo: PhotoItem, nowMillis: Long) {
        isCycling = false
        activePhoto = photo
        targetPhoto = null
        lastCycleTime = nowMillis
    }

    fun onTransitionFailed() {
        isCycling = false
        targetPhoto = null
    }

    fun resetActivePhoto(photo: PhotoItem?) {
        activePhoto = photo
    }

    private fun isCropOrFramingChanged(newPhoto: PhotoItem, reference: PhotoItem? = activePhoto): Boolean {
        val current = reference ?: return true
        return newPhoto.cropX != current.cropX ||
            newPhoto.cropY != current.cropY ||
            newPhoto.zoom != current.zoom
    }
}
