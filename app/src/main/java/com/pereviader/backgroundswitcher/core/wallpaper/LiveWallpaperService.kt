package com.pereviader.backgroundswitcher.core.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.pereviader.backgroundswitcher.core.crop.CenterCropEngine
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    companion object {
        private const val UNLOCK_DEBOUNCE_MILLIS = 3000L
    }

    override fun onCreateEngine(): Engine {
        return LiveWallpaperEngine()
    }

    inner class LiveWallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var isVisible = false
        private var currentBitmap: Bitmap? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var lastCycleTime = 0L
        private var isCycling = false
        private var currentSettings: WallpaperSettings = WallpaperSettings()

        private val gestureDetector = GestureDetector(
            this@LiveWallpaperService,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (currentSettings.doubleTapToCycle && currentSettings.isEnabled) {
                        cycleWallpaper()
                        return true
                    }
                    return super.onDoubleTap(e)
                }
            }
        )

        private val drawRunnable = Runnable {
            cycleWallpaper()
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
            engineScope.launch {
                wallpaperRepository.getSettings().collect { settings ->
                    currentSettings = settings
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            val transitionedToVisible = visible && !isVisible
            isVisible = visible
            if (visible) {
                if (transitionedToVisible) {
                    engineScope.launch {
                        val settings = wallpaperRepository.getSettings().first()
                        currentSettings = settings
                        val now = SystemClock.elapsedRealtime()
                        val shouldCycleOnUnlock = settings.isEnabled && settings.cycleOnUnlock &&
                            (now - lastCycleTime >= UNLOCK_DEBOUNCE_MILLIS)
                        val isInitialLoad = currentBitmap == null

                        if (isInitialLoad || shouldCycleOnUnlock) {
                            lastCycleTime = now
                            cycleWallpaper()
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
            surfaceWidth = width
            surfaceHeight = height
            if (isVisible && currentBitmap == null) {
                cycleWallpaper()
            } else {
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            engineScope.cancel()
            currentBitmap?.recycle()
            currentBitmap = null
        }

        private fun cycleWallpaper() {
            if (!isVisible || isCycling) return
            isCycling = true
            val width = if (surfaceWidth > 0) surfaceWidth else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).width
            val height = if (surfaceHeight > 0) surfaceHeight else ScreenMetricsHelper.getScreenDimensions(this@LiveWallpaperService).height
            engineScope.launch {
                try {
                    val nextBitmap = cycleWallpaperUseCase.loadNextCandidateBitmap(
                        targetWidth = width,
                        targetHeight = height
                    )
                    if (nextBitmap != null) {
                        currentBitmap?.recycle()
                        currentBitmap = nextBitmap
                        drawFrame()
                    }
                } finally {
                    isCycling = false
                }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val bitmap = currentBitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    } else {
                        canvas.drawColor(Color.DKGRAY)
                    }
                }
            } catch (_: Exception) {
                // Surface might have been destroyed while drawing
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
