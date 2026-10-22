package com.pereviader.backgroundswitcher.domain.usecase

import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import javax.inject.Inject

class SyncNewPhotosUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke() {
        albumRepository.syncAllSelectedAlbums()
    }
}
