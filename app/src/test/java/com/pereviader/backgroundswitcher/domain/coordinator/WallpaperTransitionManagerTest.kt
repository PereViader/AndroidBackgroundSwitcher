package com.pereviader.backgroundswitcher.domain.coordinator

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperTransitionManagerTest {

    private val defaultSettings = WallpaperSettings(
        isEnabled = true,
        cycleOnUnlock = true,
        doubleTapToCycle = true,
        intervalMinutes = 15L
    )

    private val photo1 = PhotoItem(
        id = "photo_1",
        albumId = "album_1",
        uri = "content://media/1",
        cropX = 0.5f,
        cropY = 0.5f,
        zoom = 1.0f
    )

    private val photo2 = PhotoItem(
        id = "photo_2",
        albumId = "album_1",
        uri = "content://media/2",
        cropX = 0.5f,
        cropY = 0.5f,
        zoom = 1.0f
    )

    private val photo3 = PhotoItem(
        id = "photo_3",
        albumId = "album_1",
        uri = "content://media/3",
        cropX = 0.5f,
        cropY = 0.5f,
        zoom = 1.0f
    )

    private lateinit var bitmap1: Bitmap
    private lateinit var bitmap2: Bitmap
    private lateinit var bitmap3: Bitmap

    @Before
    fun setUp() {
        bitmap1 = mockk(relaxed = true) { every { isRecycled } returns false }
        bitmap2 = mockk(relaxed = true) { every { isRecycled } returns false }
        bitmap3 = mockk(relaxed = true) { every { isRecycled } returns false }
    }

    /**
     * Controllable [WallpaperTransitionManager.CrossfadeRunner] for deterministic frame-by-frame testing.
     */
    private class ControllableCrossfadeRunner : WallpaperTransitionManager.CrossfadeRunner {
        private var activeCallback: ((Int) -> Unit)? = null
        private var completionDeferred: CompletableDeferred<Unit>? = null
        var runCount = 0
            private set
        var cancelCount = 0
            private set

        val isRunning: Boolean
            get() = completionDeferred?.isActive == true

        override suspend fun runCrossfade(onAlphaStep: (Int) -> Unit) {
            runCount++
            activeCallback = onAlphaStep
            val deferred = CompletableDeferred<Unit>()
            completionDeferred = deferred
            try {
                deferred.await()
            } finally {
                if (activeCallback === onAlphaStep) {
                    activeCallback = null
                }
            }
        }

        fun stepAlpha(alpha: Int) {
            activeCallback?.invoke(alpha)
        }

        override fun completeImmediately() {
            activeCallback?.invoke(WallpaperTransitionManager.MAX_ALPHA)
            completionDeferred?.complete(Unit)
        }

        override fun cancel() {
            cancelCount++
            activeCallback = null
            completionDeferred?.cancel()
            completionDeferred = null
        }
    }

    @Test
    fun `double tap executes full transactional transition with crossfade and commits only after animation ends`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val crossfadeRunner = ControllableCrossfadeRunner()
        val candidateDeferred = CompletableDeferred<PhotoItem?>()
        val bitmapDeferred = CompletableDeferred<Bitmap?>()
        val markedPhotoIds = mutableListOf<String>()
        val renderedFrames = mutableListOf<WallpaperTransitionManager.FrameState>()
        var currentTime = 1000L

        var isInitialSetup = true
        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { candidateDeferred.await() },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                if (isInitialSetup && photo.id == photo1.id) bitmap1 else bitmapDeferred.await()
            },
            markPhotoDisplayed = { id -> markedPhotoIds.add(id) },
            crossfadeRunner = crossfadeRunner,
            clock = { currentTime },
            onFrameUpdated = { renderedFrames.add(it) }
        )

        // Make surface visible and complete initial load of photo1
        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = currentTime)
        advanceUntilIdle()
        isInitialSetup = false

        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.previousBitmap).isNull()
        assertThat(manager.frameState.transitionAlpha).isEqualTo(255)
        assertThat(manager.isTransitioning).isFalse()
        renderedFrames.clear()

        // Trigger DoubleTapped
        currentTime = 2000L
        val decision = manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = currentTime
        )
        assertThat(decision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)

        // Phase 1: Immediately in ResolvingCandidate BEFORE coroutine even runs
        assertThat(manager.isTransitioning).isTrue()
        assertThat(manager.coordinator.isCycling).isTrue()
        assertThat(manager.transactionState)
            .isInstanceOf(WallpaperTransitionManager.TransactionState.ResolvingCandidate::class.java)
        runCurrent()

        // Resolve candidate photo2
        candidateDeferred.complete(photo2)
        runCurrent()

        // Phase 2: In LoadingBitmap, frame still displays bitmap1 untouched
        assertThat(manager.transactionState)
            .isInstanceOf(WallpaperTransitionManager.TransactionState.LoadingBitmap::class.java)
        assertThat(manager.coordinator.targetPhoto).isEqualTo(photo2)
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.previousBitmap).isNull()

        // Complete bitmap loading
        bitmapDeferred.complete(bitmap2)
        runCurrent()

        // Phase 3: In AnimatingAndCommitting, crossfade starts at alpha = 0
        assertThat(manager.transactionState)
            .isInstanceOf(WallpaperTransitionManager.TransactionState.AnimatingAndCommitting::class.java)
        assertThat(manager.isTransitioning).isTrue()
        assertThat(manager.coordinator.isCycling).isTrue()
        assertThat(markedPhotoIds).containsExactly("photo_1", "photo_2").inOrder()
        assertThat(manager.frameState).isEqualTo(
            WallpaperTransitionManager.FrameState(
                previousBitmap = bitmap1,
                currentBitmap = bitmap2,
                transitionAlpha = 0
            )
        )

        // Step through crossfade frames
        crossfadeRunner.stepAlpha(64)
        assertThat(manager.frameState.transitionAlpha).isEqualTo(64)
        assertThat(manager.frameState.previousBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)
        assertThat(manager.coordinator.isCycling).isTrue()

        crossfadeRunner.stepAlpha(192)
        assertThat(manager.frameState.transitionAlpha).isEqualTo(192)
        assertThat(manager.coordinator.isCycling).isTrue()

        // Complete crossfade animation
        currentTime = 2400L
        crossfadeRunner.completeImmediately()
        runCurrent()

        // Phase 4: Transaction committed!
        assertThat(manager.transactionState).isEqualTo(WallpaperTransitionManager.TransactionState.Idle)
        assertThat(manager.isTransitioning).isFalse()
        assertThat(manager.coordinator.isCycling).isFalse()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo2)
        assertThat(manager.coordinator.targetPhoto).isNull()
        assertThat(manager.coordinator.lastCycleTime).isEqualTo(2400L)
        assertThat(manager.frameState).isEqualTo(
            WallpaperTransitionManager.FrameState(
                previousBitmap = null,
                currentBitmap = bitmap2,
                transitionAlpha = 255
            )
        )
    }

    @Test
    fun `concurrent double taps during candidate resolution, bitmap load, and crossfade are rejected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val crossfadeRunner = ControllableCrossfadeRunner()
        val candidateDeferred = CompletableDeferred<PhotoItem?>()
        val bitmapDeferred = CompletableDeferred<Bitmap?>()
        var candidateCallCount = 0
        var bitmapLoadCallCount = 0

        var isInitialSetup = true
        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = {
                candidateCallCount++
                candidateDeferred.await()
            },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                if (isInitialSetup && photo.id == photo1.id) {
                    bitmap1
                } else {
                    bitmapLoadCallCount++
                    bitmapDeferred.await()
                }
            },
            markPhotoDisplayed = {},
            crossfadeRunner = crossfadeRunner
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()
        isInitialSetup = false

        // Start double-tap transition
        val firstDecision = manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        assertThat(firstDecision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto::class.java)
        runCurrent()

        // 1. Concurrent double-tap while resolving candidate photo
        val duringCandidateDecision = manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2050L
        )
        assertThat(duringCandidateDecision).isEqualTo(WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling)

        // Advance to bitmap loading
        candidateDeferred.complete(photo2)
        runCurrent()

        // 2. Concurrent double-tap while loading bitmap
        val duringLoadDecision = manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2100L
        )
        assertThat(duringLoadDecision).isEqualTo(WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling)

        // Advance to crossfade animation
        bitmapDeferred.complete(bitmap2)
        runCurrent()
        crossfadeRunner.stepAlpha(120)

        // 3. Concurrent double-tap while crossfade animation is in flight
        val duringAnimationDecision = manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2250L
        )
        assertThat(duringAnimationDecision).isEqualTo(WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling)
        assertThat(manager.frameState.transitionAlpha).isEqualTo(120)
        assertThat(manager.frameState.previousBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)

        // Finish animation
        crossfadeRunner.completeImmediately()
        runCurrent()

        assertThat(candidateCallCount).isEqualTo(1)
        assertThat(bitmapLoadCallCount).isEqualTo(1)
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo2)
    }

    @Test
    fun `stale external photo emission of previous photo does not glitch or revert transition`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var nextCandidate: PhotoItem? = photo2
        val loadedPhotoIds = mutableListOf<String>()

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { nextCandidate },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                loadedPhotoIds.add(photo.id)
                when (photo.id) {
                    photo1.id -> bitmap1
                    photo2.id -> bitmap2
                    else -> bitmap3
                }
            },
            markPhotoDisplayed = {},
            crossfadeRunner = WallpaperTransitionManager.CrossfadeRunner.Immediate
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        loadedPhotoIds.clear()

        // User double-taps to transition from photo1 -> photo2
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        advanceUntilIdle()

        assertThat(manager.coordinator.activePhoto).isEqualTo(photo2)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)
        assertThat(loadedPhotoIds).containsExactly("photo_2")

        // Simulate stale emission of photo1 arriving from getCurrentPhoto() BEFORE the repository flow
        // has emitted photo2 (e.g. triggered by table-level Room invalidation during markPhotoDisplayed)
        val staleDecision = manager.onExternalPhotoChanged(photo1, defaultSettings, nowMillis = 2010L)
        assertThat(staleDecision).isEqualTo(
            WallpaperCycleCoordinator.CycleDecision.Ignored("stale_previous_photo_emission")
        )
        advanceUntilIdle()

        // Wallpaper MUST remain on photo2 and MUST NOT reload photo1!
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo2)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)
        assertThat(loadedPhotoIds).containsExactly("photo_2")

        // Now repository flow catches up and emits photo2
        val syncDecision = manager.onExternalPhotoChanged(photo2, defaultSettings, nowMillis = 2020L)
        assertThat(syncDecision).isInstanceOf(WallpaperCycleCoordinator.CycleDecision.Ignored::class.java)
        assertThat(manager.awaitingRepositorySyncPhotoId).isNull()

        // Later, if user genuinely selects photo1 externally from Preview screen, it transitions to photo1
        val genuineExternalDecision = manager.onExternalPhotoChanged(photo1, defaultSettings, nowMillis = 5000L)
        assertThat(genuineExternalDecision)
            .isInstanceOf(WallpaperCycleCoordinator.CycleDecision.TransitionTo::class.java)
        advanceUntilIdle()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
    }

    @Test
    fun `failed bitmap load rolls back transaction completely leaving previous photo and frame intact`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val markedPhotoIds = mutableListOf<String>()

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { photo2 },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                if (photo.id == photo1.id) bitmap1 else null
            },
            markPhotoDisplayed = { markedPhotoIds.add(it) }
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
        markedPhotoIds.clear()

        // Trigger double-tap where photo2 fails to load (returns null)
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        advanceUntilIdle()

        // Transaction rolled back cleanly
        assertThat(manager.transactionState).isEqualTo(WallpaperTransitionManager.TransactionState.Idle)
        assertThat(manager.isTransitioning).isFalse()
        assertThat(manager.coordinator.isCycling).isFalse()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.coordinator.targetPhoto).isNull()
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.previousBitmap).isNull()
        assertThat(manager.frameState.transitionAlpha).isEqualTo(255)
        assertThat(markedPhotoIds).isEmpty()
    }

    @Test
    fun `cycle retries next candidate within same transaction when first candidate fails to load`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val candidates = ArrayDeque(listOf(photo2, photo3))
        val markedPhotoIds = mutableListOf<String>()

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { candidates.removeFirstOrNull() },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                when (photo.id) {
                    photo1.id -> bitmap1
                    photo2.id -> null // Simulate deleted/corrupt file
                    photo3.id -> bitmap3
                    else -> null
                }
            },
            markPhotoDisplayed = { markedPhotoIds.add(it) }
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()
        markedPhotoIds.clear()

        // Double tap should skip broken photo2 and succeed with photo3 in the same transaction
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        advanceUntilIdle()

        assertThat(manager.coordinator.activePhoto).isEqualTo(photo3)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap3)
        assertThat(markedPhotoIds).containsExactly("photo_3")
        assertThat(manager.isTransitioning).isFalse()
    }

    @Test
    fun `exception during markPhotoDisplayed rolls back transaction without mutating frameState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var failMarkDisplayed = false

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { photo2 },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                if (photo.id == photo1.id) bitmap1 else bitmap2
            },
            markPhotoDisplayed = {
                if (failMarkDisplayed) throw IllegalStateException("Disk write failure")
            }
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()

        failMarkDisplayed = true
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        advanceUntilIdle()

        assertThat(manager.transactionState).isEqualTo(WallpaperTransitionManager.TransactionState.Idle)
        assertThat(manager.coordinator.isCycling).isFalse()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.previousBitmap).isNull()
        assertThat(manager.awaitingRepositorySyncPhotoId).isNotEqualTo(photo2.id)
    }

    @Test
    fun `explicit override during crossfade cancels previous transaction cleanly without flashing low-alpha bitmap`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val crossfadeRunner = ControllableCrossfadeRunner()

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { photo2 },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                when (photo.id) {
                    photo1.id -> bitmap1
                    photo2.id -> bitmap2
                    photo3.id -> bitmap3
                    else -> null
                }
            },
            markPhotoDisplayed = {},
            crossfadeRunner = crossfadeRunner
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        crossfadeRunner.completeImmediately()
        advanceUntilIdle()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)

        // Start double-tap transition photo1 -> photo2
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        runCurrent()

        // Crossfade is only at alpha = 30 (bitmap1 is still 88% dominant on screen)
        crossfadeRunner.stepAlpha(30)
        assertThat(manager.frameState.previousBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)
        assertThat(manager.frameState.transitionAlpha).isEqualTo(30)

        // User explicitly selects photo3 externally
        manager.onExternalPhotoChanged(photo3, defaultSettings, nowMillis = 2100L)
        runCurrent()

        // Cancellation of tx1 must NOT clobber tx2's state, and baseline must keep dominant bitmap1
        assertThat(manager.isTransitioning).isTrue()
        assertThat(manager.coordinator.isCycling).isTrue()
        assertThat(manager.coordinator.targetPhoto).isEqualTo(photo3)
        assertThat(manager.frameState.previousBitmap).isEqualTo(bitmap1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap3)
        assertThat(manager.frameState.transitionAlpha).isEqualTo(0)

        // Complete tx2 crossfade
        crossfadeRunner.completeImmediately()
        runCurrent()

        assertThat(manager.isTransitioning).isFalse()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo3)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap3)
        assertThat(manager.frameState.previousBitmap).isNull()
        assertThat(manager.frameState.transitionAlpha).isEqualTo(255)
    }

    @Test
    fun `hiding surface mid-crossfade snaps crossfade to completion and commits transaction`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val crossfadeRunner = ControllableCrossfadeRunner()

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { photo2 },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                if (photo.id == photo1.id) bitmap1 else bitmap2
            },
            markPhotoDisplayed = {},
            crossfadeRunner = crossfadeRunner
        )

        manager.onSurfaceChanged(1080, 2400)
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        advanceUntilIdle()

        // Start double-tap transition photo1 -> photo2
        manager.onTrigger(
            WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
            defaultSettings,
            nowMillis = 2000L
        )
        runCurrent()
        crossfadeRunner.stepAlpha(100)
        assertThat(manager.frameState.isCrossfading).isTrue()

        // Surface becomes invisible mid-crossfade
        manager.onVisibilityChanged(false, defaultSettings, nowMillis = 2150L)
        runCurrent()

        // Crossfade snaps to completion and transaction commits cleanly
        assertThat(manager.isTransitioning).isFalse()
        assertThat(manager.coordinator.isCycling).isFalse()
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo2)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap2)
        assertThat(manager.frameState.previousBitmap).isNull()
        assertThat(manager.frameState.transitionAlpha).isEqualTo(255)
    }

    @Test
    fun `initial visibility and surfaceChanged and external emission do not race on startup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        var loadCount = 0

        val manager = WallpaperTransitionManager(
            scope = scope,
            getNextCandidatePhoto = { photo2 },
            getCurrentPhoto = { photo1 },
            loadAndCropBitmap = { photo, _, _ ->
                loadCount++
                if (photo.id == photo1.id) bitmap1 else bitmap2
            },
            markPhotoDisplayed = {}
        )

        // Simulate simultaneous startup callbacks
        manager.onVisibilityChanged(true, defaultSettings, nowMillis = 1000L)
        manager.onSurfaceChanged(1080, 2400)
        runCurrent()
        manager.onExternalPhotoChanged(photo1, defaultSettings, nowMillis = 1005L)
        advanceUntilIdle()

        assertThat(loadCount).isEqualTo(1)
        assertThat(manager.coordinator.activePhoto).isEqualTo(photo1)
        assertThat(manager.frameState.currentBitmap).isEqualTo(bitmap1)
    }
}
