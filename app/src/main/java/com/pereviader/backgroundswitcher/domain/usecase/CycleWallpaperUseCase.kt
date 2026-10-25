package com.pereviader.backgroundswitcher.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class CycleWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository
) {
    suspend operator fun invoke(forceLiveUpdate: Boolean = false): Result<Unit> {
        val settings = wallpaperRepository.getSettings().firstOrNull()
        val isSeparateMode = settings?.isSeparateScreensMode == true
        val isLiveActive = wallpaperRepository.isLiveWallpaperActive()

        if (!isSeparateMode) {
            if (isLiveActive) {
                if (forceLiveUpdate) {
                    val photo = wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.SHARED)
                        ?: wallpaperRepository.getNextCandidatePhoto()
                    if (photo != null) {
                        wallpaperRepository.markPhotoDisplayed(photo.id, ScheduleScreenScope.SHARED)
                    }
                }
                return Result.success(Unit)
            }

            val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
            val photo = wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.SHARED)
                ?: wallpaperRepository.getNextCandidatePhoto()
                ?: return Result.failure(IllegalStateException("No photos found in active list"))

            val bitmap = wallpaperRepository.loadAndCropBitmap(
                photo = photo,
                targetWidth = dimensions.width,
                targetHeight = dimensions.height
            ) ?: return Result.failure(IllegalStateException("Failed to load and crop photo: ${photo.uri}"))

            val targetScreen = wallpaperRepository.getActiveListTargetScreen(ScheduleScreenScope.SHARED)
            val applyResult = wallpaperRepository.applyWallpaper(bitmap, targetScreen)
            if (applyResult.isSuccess) {
                wallpaperRepository.markPhotoDisplayed(photo.id, ScheduleScreenScope.SHARED)
            }
            return applyResult
        }

        // Separate Screens Mode (Independent Home & Lock Schedules)
        val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
        var homeResult: Result<Unit> = Result.success(Unit)

        if (isLiveActive) {
            if (forceLiveUpdate) {
                val homePhoto = wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.HOME)
                if (homePhoto != null) {
                    wallpaperRepository.markPhotoDisplayed(homePhoto.id, ScheduleScreenScope.HOME)
                }
            }
        } else {
            val homePhoto = wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.HOME)
            if (homePhoto != null) {
                val homeBitmap = wallpaperRepository.loadAndCropBitmap(
                    photo = homePhoto,
                    targetWidth = dimensions.width,
                    targetHeight = dimensions.height
                )
                if (homeBitmap != null) {
                    homeResult = wallpaperRepository.applyWallpaper(homeBitmap, TargetScreen.HOME)
                    if (homeResult.isSuccess) {
                        wallpaperRepository.markPhotoDisplayed(homePhoto.id, ScheduleScreenScope.HOME)
                    }
                } else {
                    homeResult = Result.failure(IllegalStateException("Failed to load Home photo: ${homePhoto.uri}"))
                }
            } else {
                homeResult = Result.failure(IllegalStateException("No photos found in Home active list"))
            }
        }

        var lockResult: Result<Unit> = Result.success(Unit)
        val lockPhoto = wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.LOCK)
        if (lockPhoto != null) {
            val lockBitmap = wallpaperRepository.loadAndCropBitmap(
                photo = lockPhoto,
                targetWidth = dimensions.width,
                targetHeight = dimensions.height
            )
            if (lockBitmap != null) {
                lockResult = wallpaperRepository.applyWallpaper(lockBitmap, TargetScreen.LOCK)
                if (lockResult.isSuccess) {
                    wallpaperRepository.markPhotoDisplayed(lockPhoto.id, ScheduleScreenScope.LOCK)
                }
            } else {
                lockResult = Result.failure(IllegalStateException("Failed to load Lock photo: ${lockPhoto.uri}"))
            }
        } else if (!isLiveActive && homeResult.isFailure) {
            lockResult = Result.failure(IllegalStateException("No photos found in Lock active list"))
        }

        return if (homeResult.isSuccess && lockResult.isSuccess) {
            Result.success(Unit)
        } else {
            if (homeResult.isFailure) homeResult else lockResult
        }
    }

    suspend fun loadNextCandidateBitmap(targetWidth: Int, targetHeight: Int): Bitmap? {
        val photo = wallpaperRepository.getNextCandidatePhoto() ?: return null
        val bitmap = wallpaperRepository.loadAndCropBitmap(photo, targetWidth, targetHeight)
        if (bitmap != null) {
            wallpaperRepository.markPhotoDisplayed(photo.id)
        }
        return bitmap
    }
}
