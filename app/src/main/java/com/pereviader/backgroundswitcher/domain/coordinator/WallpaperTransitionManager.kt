package com.pereviader.backgroundswitcher.domain.coordinator

import android.graphics.Bitmap
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pure Kotlin transactional manager for wallpaper transitions.
 *
 * Encapsulates candidate resolution, bitmap loading, state persistence, crossfade animation,
 * rollback on error, and protection against stale repository re-emissions.
 *
 * Every transition executes as a single atomic transaction:
 * 1. Synchronously marks the coordinator as cycling ([TransactionState.ResolvingCandidate] or
 *    [TransactionState.LoadingBitmap]) before any suspend point so concurrent triggers are rejected.
 * 2. Preserves a pre-transaction snapshot ([FrameState] and active [PhotoItem]). If candidate
 *    resolution, bitmap loading, or state persistence fails, rolls back completely without visual
 *    glitches or partial state mutations.
 * 3. Persists `markPhotoDisplayed` and runs the crossfade animation (`0..255`) inside the active
 *    transaction while `coordinator.isCycling` remains `true`, committing `onTransitionCompleted`
 *    only after the transition has fully settled.
 * 4. Tracks in-flight and recently committed photo IDs (`awaitingRepositorySyncPhotoId` and
 *    `lastReplacedPhotoId`) so asynchronous emissions of the previous photo from `getCurrentPhoto()`
 *    cannot trigger a false `ExternalPhotoChanged` transition back to the old photo.
 */
class WallpaperTransitionManager(
    private val scope: CoroutineScope,
    val coordinator: WallpaperCycleCoordinator = WallpaperCycleCoordinator(),
    private val getNextCandidatePhoto: suspend () -> PhotoItem?,
    private val getCurrentPhoto: suspend () -> PhotoItem? = { null },
    private val loadAndCropBitmap: suspend (photo: PhotoItem, width: Int, height: Int) -> Bitmap?,
    private val markPhotoDisplayed: suspend (photoId: String) -> Unit,
    private val crossfadeRunner: CrossfadeRunner = CrossfadeRunner.Immediate,
    private val fallbackDimensionsProvider: () -> Pair<Int, Int> = { DEFAULT_WIDTH to DEFAULT_HEIGHT },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onFrameUpdated: (FrameState) -> Unit = {},
    private val onScheduleNextCycle: (customDelayMillis: Long?) -> Unit = {}
) {
    constructor(
        scope: CoroutineScope,
        wallpaperRepository: WallpaperRepository,
        coordinator: WallpaperCycleCoordinator = WallpaperCycleCoordinator(),
        crossfadeRunner: CrossfadeRunner = CrossfadeRunner.Immediate,
        fallbackDimensionsProvider: () -> Pair<Int, Int> = { DEFAULT_WIDTH to DEFAULT_HEIGHT },
        clock: () -> Long = { System.currentTimeMillis() },
        onFrameUpdated: (FrameState) -> Unit = {},
        onScheduleNextCycle: (customDelayMillis: Long?) -> Unit = {}
    ) : this(
        scope = scope,
        coordinator = coordinator,
        getNextCandidatePhoto = { wallpaperRepository.getNextCandidatePhoto() },
        getCurrentPhoto = { wallpaperRepository.getCurrentPhoto().first() },
        loadAndCropBitmap = { photo, width, height ->
            wallpaperRepository.loadAndCropBitmap(photo, width, height)
        },
        markPhotoDisplayed = { photoId -> wallpaperRepository.markPhotoDisplayed(photoId) },
        crossfadeRunner = crossfadeRunner,
        fallbackDimensionsProvider = fallbackDimensionsProvider,
        clock = clock,
        onFrameUpdated = onFrameUpdated,
        onScheduleNextCycle = onScheduleNextCycle
    )

    companion object {
        const val MIN_ALPHA = 0
        const val MAX_ALPHA = 255
        const val MIDPOINT_ALPHA = 128
        const val MAX_CANDIDATE_ATTEMPTS = 3
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 2400
    }

    data class FrameState(
        val previousBitmap: Bitmap? = null,
        val currentBitmap: Bitmap? = null,
        val transitionAlpha: Int = MAX_ALPHA
    ) {
        val isCrossfading: Boolean
            get() = previousBitmap != null && transitionAlpha < MAX_ALPHA
    }

    sealed interface TransactionState {
        data object Idle : TransactionState

        data class ResolvingCandidate(
            val transactionId: Long,
            val reason: String,
            val animate: Boolean
        ) : TransactionState

        data class LoadingBitmap(
            val transactionId: Long,
            val targetPhoto: PhotoItem,
            val reason: String,
            val animate: Boolean
        ) : TransactionState

        data class AnimatingAndCommitting(
            val transactionId: Long,
            val targetPhoto: PhotoItem,
            val reason: String,
            val animate: Boolean
        ) : TransactionState
    }

    interface CrossfadeRunner {
        /**
         * Steps alpha from [MIN_ALPHA] to [MAX_ALPHA], invoking [onAlphaStep] on each frame,
         * and suspends until the crossfade animation completes.
         */
        suspend fun runCrossfade(onAlphaStep: (Int) -> Unit)

        /**
         * Snaps an active crossfade animation to completion immediately without cancelling
         * the surrounding transaction.
         */
        fun completeImmediately() {}

        /**
         * Cancels any active crossfade animation.
         */
        fun cancel() {}

        companion object {
            val Immediate: CrossfadeRunner = object : CrossfadeRunner {
                override suspend fun runCrossfade(onAlphaStep: (Int) -> Unit) {
                    onAlphaStep(MAX_ALPHA)
                }
            }
        }
    }

    var frameState: FrameState = FrameState()
        private set

    var transactionState: TransactionState = TransactionState.Idle
        private set

    val isTransitioning: Boolean
        get() = transactionState != TransactionState.Idle || coordinator.isCycling

    var surfaceWidth: Int = 0
        private set

    var surfaceHeight: Int = 0
        private set

    var awaitingRepositorySyncPhotoId: String? = null
        private set

    var lastReplacedPhotoId: String? = null
        private set

    private var currentTransactionId: Long = 0L
    private var activeJob: Job? = null

    fun onTrigger(
        trigger: WallpaperCycleCoordinator.CycleTrigger,
        settings: WallpaperSettings,
        nowMillis: Long = clock()
    ): WallpaperCycleCoordinator.CycleDecision {
        val decision = coordinator.evaluateTrigger(trigger, settings, nowMillis)
        handleDecision(decision)
        return decision
    }

    fun onExternalPhotoChanged(
        photo: PhotoItem?,
        settings: WallpaperSettings,
        nowMillis: Long = clock()
    ): WallpaperCycleCoordinator.CycleDecision {
        if (photo == null) {
            if (coordinator.isSurfaceVisible && frameState.currentBitmap == null && !isTransitioning) {
                loadInitialIfNeeded("external_null_fallback")
            }
            return WallpaperCycleCoordinator.CycleDecision.Ignored("external_photo_null")
        }

        // Guard against stale repository emissions of the previous photo before the repository
        // flow has caught up to the photo committed by an internal transition.
        val pendingSyncId = awaitingRepositorySyncPhotoId
        if (pendingSyncId != null) {
            when (photo.id) {
                pendingSyncId -> {
                    awaitingRepositorySyncPhotoId = null
                    lastReplacedPhotoId = null
                }
                lastReplacedPhotoId -> {
                    return WallpaperCycleCoordinator.CycleDecision.Ignored("stale_previous_photo_emission")
                }
                else -> {
                    awaitingRepositorySyncPhotoId = null
                    lastReplacedPhotoId = null
                }
            }
        }

        // Do not let an external emission interrupt an active candidate resolution for an internal cycle
        val currentState = transactionState
        if (currentState is TransactionState.ResolvingCandidate && !isInitialLoadReason(currentState.reason)) {
            if (photo.id == coordinator.activePhoto?.id && !isCropOrFramingChanged(photo, coordinator.activePhoto)) {
                return WallpaperCycleCoordinator.CycleDecision.Ignored("external_photo_unchanged_or_cycling")
            }
        }

        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.ExternalPhotoChanged(photo),
            settings,
            nowMillis
        )
        handleDecision(decision)
        return decision
    }

    fun onVisibilityChanged(
        visible: Boolean,
        settings: WallpaperSettings,
        nowMillis: Long = clock()
    ): WallpaperCycleCoordinator.CycleDecision {
        val decision = coordinator.evaluateTrigger(
            WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(visible),
            settings,
            nowMillis
        )

        if (!visible) {
            if (frameState.isCrossfading) {
                crossfadeRunner.completeImmediately()
            }
            return decision
        }

        handleDecision(decision)
        onScheduleNextCycle(null)

        if (frameState.currentBitmap == null && !isTransitioning) {
            loadInitialIfNeeded("initial_visibility")
        } else {
            onFrameUpdated(frameState)
        }
        return decision
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        if (coordinator.isSurfaceVisible) {
            if (frameState.currentBitmap == null && !isTransitioning) {
                loadInitialIfNeeded("surface_changed_initial")
            } else {
                onFrameUpdated(frameState)
            }
        }
    }

    fun handleDecision(decision: WallpaperCycleCoordinator.CycleDecision) {
        when (decision) {
            is WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto -> {
                cycleToNextPhoto(decision.reason, decision.animate)
            }
            is WallpaperCycleCoordinator.CycleDecision.TransitionTo -> {
                transitionToPhoto(decision.targetPhoto, decision.reason, decision.animate)
            }
            is WallpaperCycleCoordinator.CycleDecision.ScheduleNextTimer -> {
                onScheduleNextCycle(decision.delayMillis)
            }
            is WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling,
            is WallpaperCycleCoordinator.CycleDecision.Ignored -> Unit
        }
    }

    fun cycleToNextPhoto(reason: String, animate: Boolean = true): Boolean {
        if (!coordinator.isSurfaceVisible || isTransitioning) {
            return false
        }

        val txId = ++currentTransactionId
        val snapshotFrame = frameState
        val snapshotPhoto = coordinator.activePhoto

        transactionState = TransactionState.ResolvingCandidate(
            transactionId = txId,
            reason = reason,
            animate = animate
        )
        coordinator.onTransitionStarted(target = null)

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val attemptedIds = mutableSetOf<String>()
                snapshotPhoto?.id?.let { attemptedIds.add(it) }

                var selectedPhoto: PhotoItem? = null
                var loadedBitmap: Bitmap? = null
                val (width, height) = resolveDimensions()

                var attempts = 0
                while (attempts < MAX_CANDIDATE_ATTEMPTS && loadedBitmap == null) {
                    attempts++
                    val candidate = getNextCandidatePhoto()
                    if (currentTransactionId != txId) return@launch

                    if (candidate == null) {
                        break
                    }

                    if (candidate.id == snapshotPhoto?.id &&
                        snapshotFrame.currentBitmap != null &&
                        !isCropOrFramingChanged(candidate, snapshotPhoto)
                    ) {
                        break
                    }

                    if (!attemptedIds.add(candidate.id) && candidate.id != snapshotPhoto?.id) {
                        break
                    }

                    transactionState = TransactionState.LoadingBitmap(
                        transactionId = txId,
                        targetPhoto = candidate,
                        reason = reason,
                        animate = animate
                    )
                    coordinator.onTransitionStarted(candidate)

                    val bitmap = loadAndCropBitmap(candidate, width, height)
                    if (currentTransactionId != txId) return@launch

                    if (bitmap != null && !bitmap.isRecycled) {
                        selectedPhoto = candidate
                        loadedBitmap = bitmap
                    }
                }

                if (selectedPhoto == null || loadedBitmap == null) {
                    rollbackTransaction(txId, snapshotFrame)
                    return@launch
                }

                commitAndAnimateTransaction(
                    txId = txId,
                    photo = selectedPhoto,
                    loadedBitmap = loadedBitmap,
                    reason = reason,
                    animate = animate && snapshotFrame.currentBitmap != null,
                    snapshotFrame = snapshotFrame,
                    snapshotPhoto = snapshotPhoto,
                    shouldMarkDisplayed = true
                )
            } catch (e: CancellationException) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, snapshotFrame)
                }
                throw e
            } catch (e: Exception) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, snapshotFrame)
                }
            }
        }
        return true
    }

    fun transitionToPhoto(photo: PhotoItem, reason: String, animate: Boolean = true): Boolean {
        val currentTarget = when (val state = transactionState) {
            is TransactionState.LoadingBitmap -> state.targetPhoto
            is TransactionState.AnimatingAndCommitting -> state.targetPhoto
            else -> null
        }
        if (currentTarget != null &&
            currentTarget.id == photo.id &&
            !isCropOrFramingChanged(photo, currentTarget)
        ) {
            return false
        }

        val txId = ++currentTransactionId
        val settledSnapshotFrame = settleFrameForOverride(frameState)
        if (settledSnapshotFrame != frameState) {
            frameState = settledSnapshotFrame
        }
        val snapshotPhoto = coordinator.activePhoto

        transactionState = TransactionState.LoadingBitmap(
            transactionId = txId,
            targetPhoto = photo,
            reason = reason,
            animate = animate
        )
        coordinator.onTransitionStarted(photo)

        activeJob?.cancel()
        crossfadeRunner.cancel()

        activeJob = scope.launch {
            try {
                val (width, height) = resolveDimensions()
                val bitmap = loadAndCropBitmap(photo, width, height)
                if (currentTransactionId != txId) return@launch

                if (bitmap == null || bitmap.isRecycled) {
                    rollbackTransaction(txId, settledSnapshotFrame)
                    return@launch
                }

                commitAndAnimateTransaction(
                    txId = txId,
                    photo = photo,
                    loadedBitmap = bitmap,
                    reason = reason,
                    animate = animate && settledSnapshotFrame.currentBitmap != null,
                    snapshotFrame = settledSnapshotFrame,
                    snapshotPhoto = snapshotPhoto,
                    shouldMarkDisplayed = true
                )
            } catch (e: CancellationException) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, settledSnapshotFrame)
                }
                throw e
            } catch (e: Exception) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, settledSnapshotFrame)
                }
            }
        }
        return true
    }

    fun loadInitialIfNeeded(reason: String = "initial_load"): Boolean {
        if (!coordinator.isSurfaceVisible || frameState.currentBitmap != null || isTransitioning) {
            return false
        }

        val txId = ++currentTransactionId
        val snapshotFrame = frameState
        val snapshotPhoto = coordinator.activePhoto

        transactionState = TransactionState.ResolvingCandidate(
            transactionId = txId,
            reason = reason,
            animate = false
        )
        coordinator.onTransitionStarted(target = null)

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val initialPhoto = getCurrentPhoto() ?: getNextCandidatePhoto()
                if (currentTransactionId != txId) return@launch

                if (initialPhoto == null) {
                    rollbackTransaction(txId, snapshotFrame)
                    return@launch
                }

                transactionState = TransactionState.LoadingBitmap(
                    transactionId = txId,
                    targetPhoto = initialPhoto,
                    reason = reason,
                    animate = false
                )
                coordinator.onTransitionStarted(initialPhoto)

                val (width, height) = resolveDimensions()
                val bitmap = loadAndCropBitmap(initialPhoto, width, height)
                if (currentTransactionId != txId) return@launch

                if (bitmap == null || bitmap.isRecycled) {
                    rollbackTransaction(txId, snapshotFrame)
                    return@launch
                }

                commitAndAnimateTransaction(
                    txId = txId,
                    photo = initialPhoto,
                    loadedBitmap = bitmap,
                    reason = reason,
                    animate = false,
                    snapshotFrame = snapshotFrame,
                    snapshotPhoto = snapshotPhoto,
                    shouldMarkDisplayed = true
                )
            } catch (e: CancellationException) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, snapshotFrame)
                }
                throw e
            } catch (e: Exception) {
                if (currentTransactionId == txId) {
                    rollbackTransaction(txId, snapshotFrame)
                }
            }
        }
        return true
    }

    fun destroy() {
        currentTransactionId++
        activeJob?.cancel()
        activeJob = null
        crossfadeRunner.cancel()
        transactionState = TransactionState.Idle
        coordinator.onTransitionFailed()
        awaitingRepositorySyncPhotoId = null
        lastReplacedPhotoId = null
        frameState = FrameState()
    }

    private suspend fun commitAndAnimateTransaction(
        txId: Long,
        photo: PhotoItem,
        loadedBitmap: Bitmap,
        reason: String,
        animate: Boolean,
        snapshotFrame: FrameState,
        snapshotPhoto: PhotoItem?,
        shouldMarkDisplayed: Boolean
    ) {
        transactionState = TransactionState.AnimatingAndCommitting(
            transactionId = txId,
            targetPhoto = photo,
            reason = reason,
            animate = animate
        )

        val previousSyncId = awaitingRepositorySyncPhotoId
        val previousReplacedId = lastReplacedPhotoId
        if (shouldMarkDisplayed) {
            lastReplacedPhotoId = if (snapshotPhoto?.id != photo.id) snapshotPhoto?.id else null
            awaitingRepositorySyncPhotoId = photo.id
            try {
                markPhotoDisplayed(photo.id)
            } catch (e: Exception) {
                awaitingRepositorySyncPhotoId = previousSyncId
                lastReplacedPhotoId = previousReplacedId
                throw e
            }
            if (currentTransactionId != txId) return
        }

        val shouldAnimate = animate &&
            coordinator.isSurfaceVisible &&
            snapshotFrame.currentBitmap != null &&
            snapshotFrame.currentBitmap !== loadedBitmap

        if (shouldAnimate) {
            updateFrameState(
                FrameState(
                    previousBitmap = snapshotFrame.currentBitmap,
                    currentBitmap = loadedBitmap,
                    transitionAlpha = MIN_ALPHA
                )
            )

            crossfadeRunner.runCrossfade { alpha ->
                if (currentTransactionId == txId) {
                    val clamped = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
                    updateFrameState(
                        frameState.copy(transitionAlpha = clamped)
                    )
                }
            }
            if (currentTransactionId != txId) return
        }

        updateFrameState(
            FrameState(
                previousBitmap = null,
                currentBitmap = loadedBitmap,
                transitionAlpha = MAX_ALPHA
            )
        )

        transactionState = TransactionState.Idle
        coordinator.onTransitionCompleted(photo, clock())
        onScheduleNextCycle(null)
    }

    private fun rollbackTransaction(txId: Long, snapshotFrame: FrameState) {
        if (currentTransactionId != txId) return
        crossfadeRunner.cancel()
        transactionState = TransactionState.Idle
        coordinator.onTransitionFailed()
        val settled = FrameState(
            previousBitmap = null,
            currentBitmap = snapshotFrame.currentBitmap,
            transitionAlpha = MAX_ALPHA
        )
        if (frameState != settled) {
            updateFrameState(settled)
        }
    }

    private fun settleFrameForOverride(current: FrameState): FrameState {
        if (!current.isCrossfading) return current
        val dominantBitmap = if (current.transitionAlpha < MIDPOINT_ALPHA) {
            current.previousBitmap ?: current.currentBitmap
        } else {
            current.currentBitmap ?: current.previousBitmap
        }
        return FrameState(
            previousBitmap = null,
            currentBitmap = dominantBitmap,
            transitionAlpha = MAX_ALPHA
        )
    }

    private fun updateFrameState(newState: FrameState) {
        frameState = newState
        onFrameUpdated(newState)
    }

    private fun resolveDimensions(): Pair<Int, Int> {
        return if (surfaceWidth > 0 && surfaceHeight > 0) {
            surfaceWidth to surfaceHeight
        } else {
            fallbackDimensionsProvider()
        }
    }

    private fun isInitialLoadReason(reason: String): Boolean {
        return reason == "initial_load" ||
            reason == "initial_visibility" ||
            reason == "surface_changed_initial" ||
            reason == "external_null_fallback"
    }

    private fun isCropOrFramingChanged(newPhoto: PhotoItem, reference: PhotoItem?): Boolean {
        val current = reference ?: return true
        return newPhoto.cropX != current.cropX ||
            newPhoto.cropY != current.cropY ||
            newPhoto.zoom != current.zoom
    }
}
