package com.pereviader.backgroundswitcher.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.pereviader.backgroundswitcher.core.crop.ScreenMetricsHelper
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CycleWallpaperUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        if (wallpaperRepository.isLiveWallpaperActive()) {
            // Live wallpaper manages its own cycle without destroying live wallpaper window
            return Result.success(Unit)
        }

        val dimensions = ScreenMetricsHelper.getScreenDimensions(context)
        val photo = wallpaperRepository.getNextCandidatePhoto()
            ?: return Result.failure(IllegalStateException("No photos found in selected albums"))

        val bitmap = wallpaperRepository.loadAndCropBitmap(
            photo = photo,
            targetWidth = dimensions.width,
            targetHeight = dimensions.height
        ) ?: return Result.failure(IllegalStateException("Failed to load and crop photo: ${photo.uri}"))

        val applyResult = wallpaperRepository.applyWallpaper(bitmap)
        if (applyResult.isSuccess) {
            wallpaperRepository.markPhotoDisplayed(photo.id)
        }
        return applyResult
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
