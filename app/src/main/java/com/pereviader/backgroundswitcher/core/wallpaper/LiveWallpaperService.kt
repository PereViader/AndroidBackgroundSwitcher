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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
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
        private const val DOUBLE_TAP_MAX_DELAY_MILLIS = 650L
        private const val DOUBLE_TAP_SLOP_PIXELS = 500
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
        private var isVisible = false
        private var previousBitmap: Bitmap? = null
        private var currentBitmap: Bitmap? = null
        private var transitionAlpha = 255
        private var transitionAnimator: ValueAnimator? = null
        private var transitionJob: Job? = null
        private var activePhotoId: String? = null
        private var activeCropX: Float? = null
        private var activeCropY: Float? = null
        private var activeZoom: Float? = null
        private var targetPhotoId: String? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var lastCycleTime = 0L
        private var lastCycleAttemptTime = 0L
        private var lastUnlockCycleTime = 0L
        private var pendingUnlockCycle = false
        private var isCycling = false
        private var currentSettings: WallpaperSettings = WallpaperSettings()

        // Double-tap tracking
        private var lastTapTime = 0L
        private var lastTapX = 0
        private var lastTapY = 0

        private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val drawMatrix = Matrix()

        private val gestureDetector = GestureDetector(
            this@LiveWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    Log.d(TAG, "GestureDetector.onDoubleTap triggered")
                    if (currentSettings.doubleTapToCycle && currentSettings.isEnabled) {
                        cycleWallpaper("gestureDetector.onDoubleTap")
                        return true
                    }
                    return super.onDoubleTap(e)
                }
            }
        )

        private val drawRunnable = Runnable {
            Log.d(TAG, "drawRunnable timer fired")
            if (isVisible && currentSettings.isEnabled) {
                cycleWallpaper("timer")
            } else {
                scheduleNextCycle()
            }
        }

        // Dynamic receiver for screen unlock & screen on events
        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "unlockReceiver received action: ${intent?.action}")
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUnlockCycleTime < UNLOCK_DEBOUNCE_MILLIS) return
                        lastUnlockCycleTime = now
                        if (currentSettings.isEnabled && currentSettings.cycleOnUnlock) {
                            Log.d(TAG, "Handling ACTION_USER_PRESENT: isVisible=$isVisible")
                            if (isVisible) {
                                cycleWallpaper("user_present")
                            } else {
                                pendingUnlockCycle = true
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val now = SystemClock.elapsedRealtime()
                        val intervalMillis = currentSettings.intervalMillis
                        if (currentSettings.isEnabled && lastCycleTime > 0L && (now - lastCycleTime >= intervalMillis)) {
                            Log.d(TAG, "Cycling wallpaper due to interval elapsed on ACTION_SCREEN_ON")
                            cycleWallpaper("screen_on_interval")
                        }
                    }
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
                        val isDifferentPhoto = photo.id != activePhotoId && photo.id != targetPhotoId
                        val isCropChanged = photo.id == activePhotoId &&
                            (photo.cropX != activeCropX || photo.cropY != activeCropY || photo.zoom != activeZoom)

                        if ((isDifferentPhoto || isCropChanged) && !isCycling) {
                            Log.d(TAG, "External photo/crop change detected: ${photo.id}, isDifferent=$isDifferentPhoto, isCropChanged=$isCropChanged")
                            transitionToPhoto(
                                photo = photo,
                                reason = if (isCropChanged) "crop_updated" else "external_selection",
                                animate = (currentBitmap != null && isDifferentPhoto)
                            )
                        }
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                handleTap(event.x.toInt(), event.y.toInt(), "onTouchEvent")
            }
            gestureDetector.onTouchEvent(event)
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
            if (!currentSettings.isEnabled || !currentSettings.doubleTapToCycle) return
            val now = SystemClock.elapsedRealtime()
            val timeDiff = now - lastTapTime
            val dx = x - lastTapX
            val dy = y - lastTapY
            val distanceSq = dx * dx + dy * dy

            Log.d(TAG, "handleTap ($source): timeDiff=${timeDiff}ms, dx=$dx, dy=$dy")

            if (timeDiff in 50L..DOUBLE_TAP_MAX_DELAY_MILLIS && distanceSq <= DOUBLE_TAP_SLOP_PIXELS * DOUBLE_TAP_SLOP_PIXELS) {
                Log.d(TAG, "Double-tap detected from $source! Cycling wallpaper...")
                lastTapTime = 0L
                cycleWallpaper("double_tap")
            } else {
                lastTapTime = now
                lastTapX = x
                lastTapY = y
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(TAG, "onVisibilityChanged: visible=$visible, was=$isVisible")
            val transitionedToVisible = visible && !isVisible
            isVisible = visible

            if (visible) {
                scheduleNextCycle()
                if (transitionedToVisible) {
                    val now = SystemClock.elapsedRealtime()
                    engineScope.launch {
                        val settings = wallpaperRepository.getSettings().first()
                        currentSettings = settings
                        val intervalMillis = currentSettings.intervalMillis
                        val intervalElapsed = (lastCycleTime > 0L) && (now - lastCycleTime >= intervalMillis)
                        val isInitialLoad = currentBitmap == null
                        val shouldCycleOnPendingUnlock = pendingUnlockCycle &&
                            (now - lastUnlockCycleTime < 10_000L) &&
                            currentSettings.isEnabled && currentSettings.cycleOnUnlock
                        pendingUnlockCycle = false

                        if (isInitialLoad || intervalElapsed || shouldCycleOnPendingUnlock) {
                            val reason = when {
                                shouldCycleOnPendingUnlock -> "pending_user_present"
                                isInitialLoad -> "initial_visibility"
                                else -> "visibility_change"
                            }
                            cycleWallpaper(reason)
                        } else {
                            drawFrame()
                        }
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
            transitionJob?.cancel()
            transitionAnimator?.let { anim ->
                anim.removeAllListeners()
                anim.cancel()
            }
            transitionAnimator = null
            engineScope.cancel()
            currentBitmap = null
            previousBitmap = null
            activePhotoId = null
            activeCropX = null
            activeCropY = null
            activeZoom = null
            targetPhotoId = null
            pendingUnlockCycle = false
        }

        private fun scheduleNextCycle() {
            handler.removeCallbacks(drawRunnable)
            if (!isVisible || !currentSettings.isEnabled || currentSettings.intervalMillis <= 0) return

            val intervalMillis = currentSettings.intervalMillis
            val now = SystemClock.elapsedRealtime()
            val elapsed = if (lastCycleTime > 0L) now - lastCycleTime else 0L
            val delay = (intervalMillis - elapsed).coerceIn(1000L, intervalMillis)
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
            val isCropChangeOnly = photo.id == activePhotoId &&
                (photo.cropX != activeCropX || photo.cropY != activeCropY || photo.zoom != activeZoom)

            if (photo.id == activePhotoId && currentBitmap != null && !isCropChangeOnly) {
                Log.d(TAG, "transitionToPhoto ($reason) skipped: photo ${photo.id} already active with matching crop")
                return
            }
            if (photo.id == targetPhotoId && isCycling) {
                Log.d(TAG, "transitionToPhoto ($reason) skipped: photo ${photo.id} already in-flight")
                return
            }

            targetPhotoId = photo.id
            isCycling = true

            transitionJob?.cancel()
            transitionJob = engineScope.launch {
                try {
                    val width = if (surfaceWidth > 0) surfaceWidth else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).width
                    val height = if (surfaceHeight > 0) surfaceHeight else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).height
                    Log.d(TAG, "transitionToPhoto ($reason) loading photo ${photo.id} [${width}x${height}]...")

                    val bitmap = wallpaperRepository.loadAndCropBitmap(photo, width, height)
                    if (bitmap != null) {
                        activePhotoId = photo.id
                        activeCropX = photo.cropX
                        activeCropY = photo.cropY
                        activeZoom = photo.zoom
                        targetPhotoId = null
                        wallpaperRepository.markPhotoDisplayed(photo.id)
                        Log.d(TAG, "transitionToPhoto ($reason) successfully loaded ${photo.id}, starting crossfade")
                        updateActiveBitmap(bitmap, animateTransition = (animate && !isCropChangeOnly))
                        scheduleNextCycle()
                    } else {
                        Log.w(TAG, "transitionToPhoto ($reason): Failed to load/crop bitmap for ${photo.id}")
                        targetPhotoId = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "transitionToPhoto ($reason) error", e)
                    targetPhotoId = null
                } finally {
                    isCycling = false
                }
            }
        }

        private fun cycleWallpaper(reason: String = "") {
            if (!isVisible || isCycling) {
                Log.d(TAG, "cycleWallpaper ($reason) skipped: isVisible=$isVisible, isCycling=$isCycling")
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastCycleAttemptTime < 800L) {
                Log.d(TAG, "cycleWallpaper ($reason) debounced")
                return
            }
            lastCycleAttemptTime = now
            lastCycleTime = now

            engineScope.launch {
                try {
                    val photo = wallpaperRepository.getNextCandidatePhoto()
                    if (photo == null) {
                        Log.w(TAG, "cycleWallpaper ($reason): No candidate photo returned")
                        return@launch
                    }
                    if (photo.id == activePhotoId && currentBitmap != null &&
                        photo.cropX == activeCropX && photo.cropY == activeCropY && photo.zoom == activeZoom
                    ) {
                        Log.d(TAG, "cycleWallpaper ($reason): Candidate photo ${photo.id} is already active")
                        return@launch
                    }
                    transitionToPhoto(photo, reason = reason, animate = (currentBitmap != null))
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
