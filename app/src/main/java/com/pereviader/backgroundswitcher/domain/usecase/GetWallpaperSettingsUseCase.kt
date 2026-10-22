package com.pereviader.backgroundswitcher.domain.usecase

import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWallpaperSettingsUseCase @Inject constructor(
    private val wallpaperRepository: WallpaperRepository
) {
    operator fun invoke(): Flow<WallpaperSettings> = wallpaperRepository.getSettings()
}
