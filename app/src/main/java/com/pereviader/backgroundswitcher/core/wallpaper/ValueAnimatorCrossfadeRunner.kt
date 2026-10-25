package com.pereviader.backgroundswitcher.core.wallpaper

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.pereviader.backgroundswitcher.domain.coordinator.WallpaperTransitionManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android [ValueAnimator]-backed implementation of [WallpaperTransitionManager.CrossfadeRunner].
 */
class ValueAnimatorCrossfadeRunner(
    private val durationMillis: Long = DEFAULT_DURATION_MILLIS
) : WallpaperTransitionManager.CrossfadeRunner {

    companion object {
        const val DEFAULT_DURATION_MILLIS = 400L
    }

    private var activeAnimator: ValueAnimator? = null
    private var activeContinuation: CancellableContinuation<Unit>? = null

    override suspend fun runCrossfade(onAlphaStep: (Int) -> Unit) {
        cancel()
        suspendCancellableCoroutine { continuation ->
            activeContinuation = continuation
            val animator = ValueAnimator.ofInt(
                WallpaperTransitionManager.MIN_ALPHA,
                WallpaperTransitionManager.MAX_ALPHA
            ).apply {
                duration = durationMillis
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    if (continuation.isActive) {
                        onAlphaStep(anim.animatedValue as Int)
                    }
                }
                doOnEnd {
                    if (activeAnimator === this) {
                        activeAnimator = null
                    }
                    if (activeContinuation === continuation) {
                        activeContinuation = null
                    }
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
            activeAnimator = animator
            continuation.invokeOnCancellation {
                if (activeAnimator === animator) {
                    activeAnimator = null
                }
                if (activeContinuation === continuation) {
                    activeContinuation = null
                }
                animator.removeAllListeners()
                animator.cancel()
            }
            animator.start()
        }
    }

    override fun completeImmediately() {
        val animator = activeAnimator
        if (animator != null) {
            activeAnimator = null
            animator.end()
        } else {
            val cont = activeContinuation
            activeContinuation = null
            if (cont != null && cont.isActive) {
                cont.resume(Unit)
            }
        }
    }

    override fun cancel() {
        val animator = activeAnimator
        activeAnimator = null
        activeContinuation = null
        if (animator != null) {
            animator.removeAllListeners()
            animator.cancel()
        }
    }
}
