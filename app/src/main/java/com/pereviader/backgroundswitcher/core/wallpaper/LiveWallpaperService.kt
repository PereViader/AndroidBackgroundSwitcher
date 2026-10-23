package com.pereviader.backgroundswitcher.core.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.pereviader.backgroundswitcher.core.crop.CenterCropEngine
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiveWallpaperService : WallpaperService() {

    @Inject
    lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase

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

        private val drawRunnable = Runnable {
            cycleWallpaper()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible) {
                drawFrame()
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
            drawFrame()
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
            if (!isVisible) return
            engineScope.launch {
                val nextBitmap = cycleWallpaperUseCase.loadNextCandidateBitmap(
                    targetWidth = surfaceWidth,
                    targetHeight = surfaceHeight
                )
                if (nextBitmap != null) {
                    currentBitmap?.recycle()
                    currentBitmap = nextBitmap
                    drawFrame()
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
