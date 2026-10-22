package com.pereviader.backgroundswitcher.domain.usecase

import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import javax.inject.Inject

class UpdateWallpaperSettingsUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository
) {
    suspend operator fun invoke(settings: WallpaperSettings) {
        wallpaperRepository.updateSettings(settings)
    }
}
