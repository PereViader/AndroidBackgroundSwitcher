package com.pereviader.backgroundswitcher.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WallpaperCycleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cycleWallpaperUseCase: CycleWallpaperUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = cycleWallpaperUseCase()
        return if (result.isSuccess) {
            Result.success()
        } else {
            // Retry if transient failure
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "wallpaper_cycle_worker"
    }
}
