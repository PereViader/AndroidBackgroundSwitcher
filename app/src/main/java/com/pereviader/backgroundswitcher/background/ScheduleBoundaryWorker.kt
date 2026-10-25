package com.pereviader.backgroundswitcher.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduleBoundaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cycleWallpaperUseCase: CycleWallpaperUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val workScheduler: WorkScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val result = cycleWallpaperUseCase(forceLiveUpdate = true)
        val nextTransition = scheduleRepository.findNextBoundaryTransition()
        workScheduler.scheduleNextBoundaryTransition(nextTransition?.delayMillis)

        return if (result.isSuccess) {
            Result.success()
        } else {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "schedule_boundary_worker"
    }
}
