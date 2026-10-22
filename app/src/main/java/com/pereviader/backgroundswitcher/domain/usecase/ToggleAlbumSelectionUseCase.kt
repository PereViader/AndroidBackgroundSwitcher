package com.pereviader.backgroundswitcher.domain.usecase

import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import javax.inject.Inject

class ToggleAlbumSelectionUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke(albumId: String, isSelected: Boolean) {
        albumRepository.toggleAlbumSelection(albumId, isSelected)
    }
}
