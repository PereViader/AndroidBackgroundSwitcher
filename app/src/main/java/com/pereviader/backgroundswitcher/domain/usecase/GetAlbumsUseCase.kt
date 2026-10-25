package com.pereviader.backgroundswitcher.domain.usecase

import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAlbumsUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    operator fun invoke(): Flow<List<Album>> = albumRepository.getAlbums()
    operator fun invoke(listId: String?): Flow<List<Album>> = albumRepository.getAlbums(listId)
    suspend fun refresh() = albumRepository.refreshDeviceAlbums()
}
