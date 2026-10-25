package com.pereviader.backgroundswitcher.core.wallpaper

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.coordinator.DoubleTapDetector
import com.pereviader.backgroundswitcher.domain.coordinator.WallpaperCycleCoordinator
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class LiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    companion object {
        private const val TAG = "LiveWallpaperService"
        private const val UNLOCK_DEBOUNCE_MILLIS = 1500L
        private const val CROSSFADE_DURATION_MILLIS = 400L
    }

    override fun onCreateEngine(): Engine {
        Log.d(TAG, "onCreateEngine called")
        return LiveWallpaperEngine()
    }

    inner class LiveWallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val coordinator = WallpaperCycleCoordinator()
        private val doubleTapDetector = DoubleTapDetector()
        private var isVisible = false
        private var previousBitmap: Bitmap? = null
        private var currentBitmap: Bitmap? = null
        private var transitionAlpha = 255
        private var transitionAnimator: ValueAnimator? = null
        private var transitionJob: Job? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var lastCycleAttemptTime = 0L
        private var currentSettings: WallpaperSettings = WallpaperSettings()

        private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val drawMatrix = Matrix()

        private val drawRunnable = Runnable {
            Log.d(TAG, "drawRunnable timer fired")
            val now = SystemClock.elapsedRealtime()
            val decision = coordinator.evaluateTrigger(
                WallpaperCycleCoordinator.CycleTrigger.TimerFired,
                currentSettings,
                now
            )
            handleDecision(decision)
        }

        // Dynamic receiver for screen unlock & screen on events
        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "unlockReceiver received action: ${intent?.action}")
                val now = SystemClock.elapsedRealtime()
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        val decision = coordinator.evaluateTrigger(
                            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(isSurfaceVisible = isVisible),
                            currentSettings,
                            now
                        )
                        handleDecision(decision)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val decision = coordinator.evaluateTrigger(
                            WallpaperCycleCoordinator.CycleTrigger.ScreenTurnedOn(now),
                            currentSettings,
                            now
                        )
                        handleDecision(decision)
                    }
                }
            }
        }

        private fun handleDecision(decision: WallpaperCycleCoordinator.CycleDecision) {
            when (decision) {
                is WallpaperCycleCoordinator.CycleDecision.RequestNextPhoto -> {
                    cycleWallpaper(decision.reason, decision.animate)
                }
                is WallpaperCycleCoordinator.CycleDecision.TransitionTo -> {
                    transitionToPhoto(decision.targetPhoto, decision.reason, decision.animate)
                }
                is WallpaperCycleCoordinator.CycleDecision.ScheduleNextTimer -> {
                    scheduleNextCycle(decision.delayMillis)
                }
                is WallpaperCycleCoordinator.CycleDecision.SkipAlreadyCycling -> {
                    Log.d(TAG, "Cycle skipped: already cycling")
                }
                is WallpaperCycleCoordinator.CycleDecision.Ignored -> {
                    Log.d(TAG, "Cycle ignored: ${decision.reason}")
                }
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            Log.d(TAG, "Engine.onCreate")
            setTouchEventsEnabled(true)

            // Register dynamic receiver for unlock and screen on
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                registerReceiver(unlockReceiver, filter)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register unlockReceiver", e)
            }

            // Collect settings updates
            engineScope.launch {
                wallpaperRepository.getSettings().collect { settings ->
                    val intervalChanged = currentSettings.intervalSeconds != settings.intervalSeconds ||
                        currentSettings.intervalMinutes != settings.intervalMinutes
                    val enabledChanged = currentSettings.isEnabled != settings.isEnabled
                    currentSettings = settings
                    Log.d(TAG, "Settings updated: intervalSeconds=${settings.intervalSeconds}, enabled=${settings.isEnabled}, doubleTap=${settings.doubleTapToCycle}, unlock=${settings.cycleOnUnlock}")

                    if (intervalChanged || enabledChanged) {
                        scheduleNextCycle()
                    }
                }
            }

            // Sync with external photo selections (e.g. from Preview screen)
            engineScope.launch {
                wallpaperRepository.getCurrentPhoto().collect { photo ->
                    if (photo != null) {
                        val now = SystemClock.elapsedRealtime()
                        val decision = coordinator.evaluateTrigger(
                            WallpaperCycleCoordinator.CycleTrigger.ExternalPhotoChanged(photo),
                            currentSettings,
                            now
                        )
                        handleDecision(decision)
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val index = event.actionIndex
                    handleTap(event.getX(index).toInt(), event.getY(index).toInt(), "onTouchEvent")
                }
            }
            super.onTouchEvent(event)
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP || action == "android.wallpaper.tap" ||
                action == WallpaperManager.COMMAND_SECONDARY_TAP || action == "android.wallpaper.secondaryTap"
            ) {
                handleTap(x, y, "onCommand($action)")
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun handleTap(x: Int, y: Int, source: String) {
            if (!currentSettings.doubleTapToCycle) return
            val now = SystemClock.elapsedRealtime()
            val isDoubleTap = doubleTapDetector.onUserTap(x, y, now)
            Log.d(TAG, "handleTap ($source): isDoubleTap=$isDoubleTap, x=$x, y=$y")

            if (isDoubleTap) {
                Log.d(TAG, "Double-tap detected from $source! Cycling wallpaper...")
                val decision = coordinator.evaluateTrigger(
                    WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
                    currentSettings,
                    now
                )
                handleDecision(decision)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(TAG, "onVisibilityChanged: visible=$visible, was=$isVisible")
            val transitionedToVisible = visible && !isVisible
            isVisible = visible

            val now = SystemClock.elapsedRealtime()
            val decision = coordinator.evaluateTrigger(
                WallpaperCycleCoordinator.CycleTrigger.SurfaceVisibilityChanged(visible),
                currentSettings,
                now
            )
            handleDecision(decision)

            if (visible) {
                scheduleNextCycle()
                if (transitionedToVisible) {
                    if (currentBitmap == null) {
                        cycleWallpaper("initial_visibility")
                    } else {
                        drawFrame()
                    }
                } else {
                    drawFrame()
                }
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d(TAG, "onSurfaceChanged: width=$width, height=$height")
            surfaceWidth = width
            surfaceHeight = height
            if (isVisible) {
                if (currentBitmap == null) {
                    cycleWallpaper("surface_changed_initial")
                } else {
                    drawFrame()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "onSurfaceDestroyed")
            isVisible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.d(TAG, "Engine.onDestroy")
            try {
                unregisterReceiver(unlockReceiver)
            } catch (_: Exception) {}
            handler.removeCallbacks(drawRunnable)
            doubleTapDetector.reset()
            transitionJob?.cancel()
            transitionAnimator?.let { anim ->
                anim.removeAllListeners()
                anim.cancel()
            }
            transitionAnimator = null
            engineScope.cancel()
            currentBitmap = null
            previousBitmap = null
        }

        private fun scheduleNextCycle(customDelayMillis: Long? = null) {
            handler.removeCallbacks(drawRunnable)
            if (!isVisible || !currentSettings.isEnabled || currentSettings.intervalMillis <= 0) return

            val intervalMillis = currentSettings.intervalMillis
            val delay = customDelayMillis ?: run {
                val now = SystemClock.elapsedRealtime()
                val elapsed = if (coordinator.lastCycleTime > 0L) now - coordinator.lastCycleTime else 0L
                (intervalMillis - elapsed).coerceIn(1000L, intervalMillis)
            }
            Log.d(TAG, "scheduleNextCycle: delay=${delay}ms, interval=${intervalMillis}ms")
            handler.postDelayed(drawRunnable, delay)
        }

        private fun updateActiveBitmap(nextBitmap: Bitmap, animateTransition: Boolean = true) {
            transitionAnimator?.let { anim ->
                anim.removeAllListeners()
                anim.cancel()
            }
            transitionAnimator = null

            if (currentBitmap != null && animateTransition && isVisible) {
                previousBitmap = currentBitmap
                currentBitmap = nextBitmap
                transitionAlpha = 0

                // Render baseline frame at transitionAlpha = 0 before animator kicks off
                drawFrame()

                transitionAnimator = ValueAnimator.ofInt(0, 255).apply {
                    duration = CROSSFADE_DURATION_MILLIS
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        transitionAlpha = anim.animatedValue as Int
                        drawFrame()
                    }
                    doOnEnd {
                        previousBitmap = null
                        transitionAlpha = 255
                        drawFrame()
                    }
                    start()
                }
            } else {
                previousBitmap = null
                currentBitmap = nextBitmap
                transitionAlpha = 255
                drawFrame()
            }
        }

        private fun transitionToPhoto(photo: PhotoItem, reason: String, animate: Boolean = true) {
            coordinator.onTransitionStarted(photo)
            transitionJob?.cancel()
            transitionJob = engineScope.launch {
                try {
                    val width = if (surfaceWidth > 0) surfaceWidth else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).width
                    val height = if (surfaceHeight > 0) surfaceHeight else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).height
                    Log.d(TAG, "transitionToPhoto ($reason) loading photo ${photo.id} [${width}x${height}]...")

                    val bitmap = wallpaperRepository.loadAndCropBitmap(photo, width, height)
                    if (bitmap != null) {
                        coordinator.onTransitionCompleted(photo, SystemClock.elapsedRealtime())
                        wallpaperRepository.markPhotoDisplayed(photo.id)
                        Log.d(TAG, "transitionToPhoto ($reason) successfully loaded ${photo.id}, starting crossfade")
                        updateActiveBitmap(bitmap, animateTransition = animate)
                        scheduleNextCycle()
                    } else {
                        Log.w(TAG, "transitionToPhoto ($reason): Failed to load/crop bitmap for ${photo.id}")
                        coordinator.onTransitionFailed()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "transitionToPhoto ($reason) error", e)
                    coordinator.onTransitionFailed()
                }
            }
        }

        private fun cycleWallpaper(reason: String = "", animate: Boolean = true) {
            if (!isVisible || coordinator.isCycling) {
                Log.d(TAG, "cycleWallpaper ($reason) skipped: isVisible=$isVisible, isCycling=${coordinator.isCycling}")
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastCycleAttemptTime < 250L) {
                Log.d(TAG, "cycleWallpaper ($reason) debounced")
                return
            }
            lastCycleAttemptTime = now

            engineScope.launch {
                try {
                    val photo = wallpaperRepository.getNextCandidatePhoto()
                    if (photo == null) {
                        Log.w(TAG, "cycleWallpaper ($reason): No candidate photo returned")
                        return@launch
                    }
                    if (photo.id == coordinator.activePhoto?.id && currentBitmap != null &&
                        photo.cropX == coordinator.activePhoto?.cropX &&
                        photo.cropY == coordinator.activePhoto?.cropY &&
                        photo.zoom == coordinator.activePhoto?.zoom
                    ) {
                        Log.d(TAG, "cycleWallpaper ($reason): Candidate photo ${photo.id} is already active")
                        return@launch
                    }
                    transitionToPhoto(photo, reason = reason, animate = (currentBitmap != null && animate))
                } catch (e: Exception) {
                    Log.e(TAG, "cycleWallpaper ($reason) error", e)
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        holder.lockHardwareCanvas()
                    } catch (_: Exception) {
                        holder.lockCanvas()
                    }
                } else {
                    holder.lockCanvas()
                }

                if (canvas != null) {
                    // Always clear to clean solid black to eliminate dirty swapchain buffer artifacts
                    canvas.drawColor(Color.BLACK)

                    val prev = previousBitmap
                    if (prev != null && !prev.isRecycled && transitionAlpha < 255) {
                        drawPaint.alpha = 255
                        drawCoverBitmap(canvas, prev, drawPaint)
                    }

                    val curr = currentBitmap
                    if (curr != null && !curr.isRecycled) {
                        drawPaint.alpha = transitionAlpha
                        drawCoverBitmap(canvas, curr, drawPaint)
                    } else if (prev == null) {
                        canvas.drawColor(Color.DKGRAY)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "drawFrame error", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun drawCoverBitmap(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
            if (bitmap.width == surfaceWidth && bitmap.height == surfaceHeight) {
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            } else {
                val scale = max(
                    surfaceWidth.toFloat() / bitmap.width.toFloat(),
                    surfaceHeight.toFloat() / bitmap.height.toFloat()
                )
                val dx = (surfaceWidth - bitmap.width * scale) / 2f
                val dy = (surfaceHeight - bitmap.height * scale) / 2f
                drawMatrix.reset()
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
                canvas.drawBitmap(bitmap, drawMatrix, paint)
            }
        }
    }
}
