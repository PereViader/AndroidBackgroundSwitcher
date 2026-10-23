package com.pereviader.backgroundswitcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pereviader.backgroundswitcher.background.WorkScheduler
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetAlbumsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.ToggleAlbumSelectionUseCase
import com.pereviader.backgroundswitcher.domain.usecase.UpdateWallpaperSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val toggleAlbumSelectionUseCase: ToggleAlbumSelectionUseCase,
    private val getWallpaperSettingsUseCase: GetWallpaperSettingsUseCase,
    private val updateWallpaperSettingsUseCase: UpdateWallpaperSettingsUseCase,
    private val cycleWallpaperUseCase: CycleWallpaperUseCase,
    private val albumRepository: AlbumRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val workScheduler: WorkScheduler
) : ViewModel() {

    val albums: StateFlow<List<Album>> = getAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<WallpaperSettings> = getWallpaperSettingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WallpaperSettings())

    private val _selectedPreviewAlbumId = MutableStateFlow<String?>(null)
    val selectedPreviewAlbumId: StateFlow<String?> = _selectedPreviewAlbumId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val previewPhotos: StateFlow<List<PhotoItem>> = _selectedPreviewAlbumId.flatMapLatest { albumId ->
        if (albumId != null) {
            albumRepository.getPhotosInAlbum(albumId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPreviewPhotoIndex = MutableStateFlow(0)
    val selectedPreviewPhotoIndex: StateFlow<Int> = _selectedPreviewPhotoIndex.asStateFlow()

    val currentPreviewPhoto: StateFlow<PhotoItem?> = combine(
        previewPhotos,
        _selectedPreviewPhotoIndex
    ) { photos, index ->
        if (photos.isNotEmpty()) {
            val clamped = index.coerceIn(0, photos.size - 1)
            photos[clamped]
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val previewPhoto: StateFlow<PhotoItem?> = currentPreviewPhoto

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    init {
        refreshAlbums()
        viewModelScope.launch {
            albums.collectLatest { albumList ->
                if (albumList.isNotEmpty() && (_selectedPreviewAlbumId.value == null || albumList.none { it.id == _selectedPreviewAlbumId.value })) {
                    val defaultAlbum = albumList.firstOrNull { it.isSelected } ?: albumList.first()
                    _selectedPreviewAlbumId.value = defaultAlbum.id
                }
            }
        }
        viewModelScope.launch {
            previewPhotos.collectLatest { photos ->
                if (photos.isNotEmpty() && _selectedPreviewPhotoIndex.value >= photos.size) {
                    _selectedPreviewPhotoIndex.value = photos.size - 1
                }
            }
        }
    }

    fun selectPreviewAlbum(albumId: String) {
        _selectedPreviewAlbumId.value = albumId
        _selectedPreviewPhotoIndex.value = 0
    }

    fun selectPreviewPhotoIndex(index: Int) {
        _selectedPreviewPhotoIndex.value = index
    }

    fun nextPreviewPhoto() {
        val count = previewPhotos.value.size
        if (count > 0) {
            _selectedPreviewPhotoIndex.value = (_selectedPreviewPhotoIndex.value + 1) % count
        }
    }

    fun previousPreviewPhoto() {
        val count = previewPhotos.value.size
        if (count > 0) {
            _selectedPreviewPhotoIndex.value = (_selectedPreviewPhotoIndex.value - 1 + count) % count
        }
    }

    fun togglePhotoEnabled(photoId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            wallpaperRepository.togglePhotoEnabled(photoId, isEnabled)
        }
    }

    fun openAlbumInPreview(albumId: String, photoId: String? = null) {
        _selectedPreviewAlbumId.value = albumId
        if (photoId != null) {
            val idx = previewPhotos.value.indexOfFirst { it.id == photoId }
            _selectedPreviewPhotoIndex.value = if (idx >= 0) idx else 0
        } else {
            _selectedPreviewPhotoIndex.value = 0
        }
    }

    fun refreshAlbums() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                getAlbumsUseCase.refresh()
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Error scanning albums: ${e.localizedMessage}"))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleAlbum(albumId: String, isSelected: Boolean) {
        viewModelScope.launch {
            toggleAlbumSelectionUseCase(albumId, isSelected)
        }
    }

    fun updatePhotoCustomization(photoId: String, cropX: Float?, cropY: Float?, zoom: Float?) {
        viewModelScope.launch {
            wallpaperRepository.updatePhotoCustomization(photoId, cropX, cropY, zoom)
        }
    }

    fun updatePhotoCrop(photoId: String, cropX: Float?, cropY: Float?) {
        updatePhotoCustomization(photoId, cropX, cropY, null)
    }

    fun updateSettings(newSettings: WallpaperSettings) {
        viewModelScope.launch {
            updateWallpaperSettingsUseCase(newSettings)
            if (newSettings.isEnabled) {
                workScheduler.scheduleWallpaperCycling(
                    newSettings.intervalMinutes,
                    newSettings.onlyOnWifi
                )
                workScheduler.schedulePeriodicSync(newSettings.onlyOnWifi)
            } else {
                workScheduler.cancelAll()
            }
        }
    }

    fun triggerImmediateCycle() {
        viewModelScope.launch {
            val result = cycleWallpaperUseCase()
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Wallpaper updated successfully!"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to update wallpaper: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun importGooglePhotosAlbum(url: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.importGooglePhotosAlbum(url)
            _isRefreshing.value = false
            if (result.isSuccess) {
                val album = result.getOrNull()
                _eventFlow.emit(UiEvent.ShowSnackbar("Imported '${album?.name}' (${album?.photoCount} photos)"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to import: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun syncGooglePhotosAlbum(albumId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.syncGooglePhotosAlbum(albumId)
            _isRefreshing.value = false
            if (result.isSuccess) {
                val album = result.getOrNull()
                _eventFlow.emit(UiEvent.ShowSnackbar("Synced '${album?.name}' (${album?.photoCount} photos)"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to sync: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            albumRepository.deleteAlbum(albumId)
            _eventFlow.emit(UiEvent.ShowSnackbar("Album removed"))
        }
    }

    fun getPhotosForAlbum(albumId: String) = albumRepository.getPhotosInAlbum(albumId)
}
