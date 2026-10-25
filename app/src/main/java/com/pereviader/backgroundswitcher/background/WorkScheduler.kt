package com.pereviader.backgroundswitcher.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleWallpaperCycling(intervalMinutes: Long, onlyOnWifi: Boolean) {
        // WorkManager minimum periodic interval is 15 minutes
        val effectiveMinutes = max(15L, intervalMinutes)

        // Wallpaper cycling should run reliably for local/cached media without network gating
        val periodicWork = PeriodicWorkRequestBuilder<WallpaperCycleWorker>(
            effectiveMinutes,
            TimeUnit.MINUTES,
            5,
            TimeUnit.MINUTES // Flex interval
        )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WallpaperCycleWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun schedulePeriodicSync(onlyOnWifi: Boolean) {
        val networkType = if (onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        // Sync albums every 6 hours
        val syncWork = PeriodicWorkRequestBuilder<AlbumSyncWorker>(
            6,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AlbumSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
    }

    fun scheduleNextBoundaryTransition(delayMillis: Long?) {
        if (delayMillis == null || delayMillis <= 0L) {
            workManager.cancelUniqueWork(ScheduleBoundaryWorker.WORK_NAME)
            return
        }

        val boundaryWork = OneTimeWorkRequestBuilder<ScheduleBoundaryWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            ScheduleBoundaryWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            boundaryWork
        )
    }

    fun triggerImmediateCycle() {
        val immediateWork = OneTimeWorkRequestBuilder<WallpaperCycleWorker>()
            .build()

        workManager.enqueueUniqueWork(
            "immediate_wallpaper_cycle",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WallpaperCycleWorker.WORK_NAME)
        workManager.cancelUniqueWork(AlbumSyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(ScheduleBoundaryWorker.WORK_NAME)
    }
}
