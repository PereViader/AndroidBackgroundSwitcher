package com.pereviader.backgroundswitcher.core.wallpaper

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
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.coordinator.DoubleTapDetector
import com.pereviader.backgroundswitcher.domain.coordinator.WallpaperCycleCoordinator
import com.pereviader.backgroundswitcher.domain.coordinator.WallpaperTransitionManager
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class LiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    companion object {
        private const val TAG = "LiveWallpaperService"
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
        private val crossfadeRunner = ValueAnimatorCrossfadeRunner(CROSSFADE_DURATION_MILLIS)
        private val transitionManager = WallpaperTransitionManager(
            scope = engineScope,
            wallpaperRepository = wallpaperRepository,
            coordinator = coordinator,
            crossfadeRunner = crossfadeRunner,
            fallbackDimensionsProvider = {
                val dims = ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService)
                dims.width to dims.height
            },
            clock = { SystemClock.elapsedRealtime() },
            onFrameUpdated = { frame -> drawFrame(frame) },
            onScheduleNextCycle = { customDelayMillis -> scheduleNextCycle(customDelayMillis) }
        )

        private var currentSettings: WallpaperSettings = WallpaperSettings()

        private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val drawMatrix = Matrix()

        private val drawRunnable = Runnable {
            Log.d(TAG, "drawRunnable timer fired")
            transitionManager.onTrigger(
                WallpaperCycleCoordinator.CycleTrigger.TimerFired,
                currentSettings
            )
            if (currentSettings.isSeparateScreensMode) {
                engineScope.launch(Dispatchers.IO) {
                    runCatching { cycleWallpaperUseCase(forceLiveUpdate = false) }
                }
            }
        }

        private val boundaryRunnable = Runnable {
            Log.d(TAG, "boundaryRunnable schedule transition fired")
            transitionManager.onTrigger(
                WallpaperCycleCoordinator.CycleTrigger.TimerFired,
                currentSettings
            )
            if (currentSettings.isSeparateScreensMode) {
                engineScope.launch(Dispatchers.IO) {
                    runCatching { cycleWallpaperUseCase(forceLiveUpdate = false) }
                }
            }
            scheduleNextBoundaryTimer()
        }

        // Dynamic receiver for screen unlock & screen on events
        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "unlockReceiver received action: ${intent?.action}")
                val now = SystemClock.elapsedRealtime()
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        transitionManager.onTrigger(
                            WallpaperCycleCoordinator.CycleTrigger.ScreenUnlocked(
                                isSurfaceVisible = coordinator.isSurfaceVisible
                            ),
                            currentSettings,
                            now
                        )
                        if (currentSettings.cycleOnUnlock && currentSettings.isSeparateScreensMode) {
                            engineScope.launch(Dispatchers.IO) {
                                runCatching { cycleWallpaperUseCase(forceLiveUpdate = false) }
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        transitionManager.onTrigger(
                            WallpaperCycleCoordinator.CycleTrigger.ScreenTurnedOn(now),
                            currentSettings,
                            now
                        )
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
                    val scheduleModeChanged = currentSettings.isSeparateScreensMode != settings.isSeparateScreensMode ||
                        currentSettings.fallbackListIdShared != settings.fallbackListIdShared ||
                        currentSettings.fallbackListIdHome != settings.fallbackListIdHome ||
                        currentSettings.fallbackListIdLock != settings.fallbackListIdLock
                    currentSettings = settings
                    Log.d(TAG, "Settings updated: intervalSeconds=${settings.intervalSeconds}, enabled=${settings.isEnabled}, doubleTap=${settings.doubleTapToCycle}, unlock=${settings.cycleOnUnlock}")

                    if (intervalChanged || enabledChanged) {
                        scheduleNextCycle()
                    }
                    if (scheduleModeChanged || enabledChanged) {
                        scheduleNextBoundaryTimer()
                    }
                }
            }

            // Observe schedule rule changes to keep boundary timer in sync
            engineScope.launch {
                if (::scheduleRepository.isInitialized) {
                    scheduleRepository.observeAllRules().collect {
                        scheduleNextBoundaryTimer()
                    }
                }
            }

            // Sync with external photo selections (e.g. from Preview screen)
            engineScope.launch {
                wallpaperRepository.getCurrentPhoto().collect { photo ->
                    transitionManager.onExternalPhotoChanged(photo, currentSettings)
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
                transitionManager.onTrigger(
                    WallpaperCycleCoordinator.CycleTrigger.DoubleTapped,
                    currentSettings,
                    now
                )
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.d(TAG, "onVisibilityChanged: visible=$visible, was=${coordinator.isSurfaceVisible}")
            transitionManager.onVisibilityChanged(visible, currentSettings)
            if (!visible) {
                handler.removeCallbacks(drawRunnable)
                handler.removeCallbacks(boundaryRunnable)
            } else {
                scheduleNextBoundaryTimer()
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
            transitionManager.onSurfaceChanged(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            Log.d(TAG, "onSurfaceDestroyed")
            transitionManager.onVisibilityChanged(false, currentSettings)
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(boundaryRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.d(TAG, "Engine.onDestroy")
            try {
                unregisterReceiver(unlockReceiver)
            } catch (_: Exception) {}
            handler.removeCallbacks(drawRunnable)
            handler.removeCallbacks(boundaryRunnable)
            doubleTapDetector.reset()
            transitionManager.destroy()
            engineScope.cancel()
        }

        private fun scheduleNextCycle(customDelayMillis: Long? = null) {
            handler.removeCallbacks(drawRunnable)
            if (!coordinator.isSurfaceVisible || !currentSettings.isEnabled || currentSettings.intervalMillis <= 0) return

            val intervalMillis = currentSettings.intervalMillis
            val delay = customDelayMillis ?: run {
                val now = SystemClock.elapsedRealtime()
                val elapsed = if (coordinator.lastCycleTime > 0L) now - coordinator.lastCycleTime else 0L
                (intervalMillis - elapsed).coerceIn(1000L, intervalMillis)
            }
            Log.d(TAG, "scheduleNextCycle: delay=${delay}ms, interval=${intervalMillis}ms")
            handler.postDelayed(drawRunnable, delay)
        }

        private fun scheduleNextBoundaryTimer() {
            handler.removeCallbacks(boundaryRunnable)
            if (!coordinator.isSurfaceVisible || !currentSettings.isEnabled || !::scheduleRepository.isInitialized) return
            engineScope.launch {
                val transition = runCatching { scheduleRepository.findNextBoundaryTransition() }.getOrNull()
                if (transition != null && transition.delayMillis > 0L && coordinator.isSurfaceVisible) {
                    handler.postDelayed(boundaryRunnable, transition.delayMillis)
                }
            }
        }

        private fun drawFrame(frame: WallpaperTransitionManager.FrameState = transitionManager.frameState) {
            val holder = surfaceHolder ?: return
            val width = transitionManager.surfaceWidth
            val height = transitionManager.surfaceHeight
            if (width <= 0 || height <= 0) return

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

                    val prev = frame.previousBitmap
                    if (prev != null && !prev.isRecycled && frame.transitionAlpha < WallpaperTransitionManager.MAX_ALPHA) {
                        drawPaint.alpha = WallpaperTransitionManager.MAX_ALPHA
                        drawCoverBitmap(canvas, prev, width, height, drawPaint)
                    }

                    val curr = frame.currentBitmap
                    if (curr != null && !curr.isRecycled) {
                        drawPaint.alpha = frame.transitionAlpha
                        drawCoverBitmap(canvas, curr, width, height, drawPaint)
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

        private fun drawCoverBitmap(
            canvas: Canvas,
            bitmap: Bitmap,
            surfaceWidth: Int,
            surfaceHeight: Int,
            paint: Paint
        ) {
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
