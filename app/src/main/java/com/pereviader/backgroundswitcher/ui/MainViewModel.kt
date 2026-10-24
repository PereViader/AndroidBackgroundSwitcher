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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    companion object {
        private const val WALLPAPER_UPDATE_DEBOUNCE_MILLIS = 1500L
    }

    val albums: StateFlow<List<Album>> = getAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<WallpaperSettings> = getWallpaperSettingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WallpaperSettings())

    val activeWallpaperPhoto: StateFlow<PhotoItem?> = wallpaperRepository.getCurrentPhoto()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val photosFromSelectedAlbums: Flow<List<PhotoItem>> =
        albumRepository.getPhotosFromSelectedAlbums()

    // Holds a user-selected photo from browsing (e.g. albums dialog or filmstrip)
    private val _userSelectedPhoto = MutableStateFlow<PhotoItem?>(null)
    val userSelectedPhoto: StateFlow<PhotoItem?> = _userSelectedPhoto.asStateFlow()

    private val _removedPhotoIds = MutableStateFlow<Set<String>>(emptySet())

    // The list of preview photos always ensures the currently active wallpaper photo is included
    // so the user is always able to inspect and edit its framing immediately, along with selected photos.
    val previewPhotos: StateFlow<List<PhotoItem>> = combine(
        photosFromSelectedAlbums,
        activeWallpaperPhoto,
        _userSelectedPhoto,
        _removedPhotoIds
    ) { selectedPhotos, activePhoto, userPhoto, removedIds ->
        val result = mutableListOf<PhotoItem>()
        // If the active wallpaper photo exists, was not explicitly removed, and is not present in selected photos, prepend it
        if (activePhoto != null &&
            activePhoto.id !in removedIds &&
            selectedPhotos.none { it.id == activePhoto.id || it.uri == activePhoto.uri }
        ) {
            result.add(activePhoto)
        }
        // If a user-selected photo exists and is not already present, include it
        if (userPhoto != null &&
            userPhoto.id !in removedIds &&
            (activePhoto == null || (userPhoto.id != activePhoto.id && userPhoto.uri != activePhoto.uri)) &&
            selectedPhotos.none { it.id == userPhoto.id || it.uri == userPhoto.uri }
        ) {
            result.add(userPhoto)
        }
        result.addAll(selectedPhotos.filter { it.id !in removedIds })
        result
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val previewPhoto: StateFlow<PhotoItem?> = currentPreviewPhoto

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLiveWallpaperActive = MutableStateFlow(false)
    val isLiveWallpaperActive: StateFlow<Boolean> = _isLiveWallpaperActive.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    private var updateWallpaperDebounceJob: Job? = null
    private var pendingWallpaperPhoto: PhotoItem? = null
    private var hasPendingCustomization: Boolean = false

    init {
        refreshAlbums()
        checkLiveWallpaperStatus()
        viewModelScope.launch {
            val currentSettings = getWallpaperSettingsUseCase().first()
            if (currentSettings.isEnabled) {
                workScheduler.scheduleWallpaperCycling(
                    currentSettings.intervalMinutes,
                    currentSettings.onlyOnWifi
                )
                workScheduler.schedulePeriodicSync(currentSettings.onlyOnWifi)
            }
        }

        // Keep preview page displaying the active wallpaper photo by default,
        // unless the user has actively selected a different photo in this session.
        var lastSeenActivePhotoId: String? = null
        viewModelScope.launch {
            combine(previewPhotos, activeWallpaperPhoto) { photos, activePhoto ->
                photos to activePhoto
            }.collectLatest { (photos, activePhoto) ->
                if (photos.isEmpty()) {
                    _selectedPreviewPhotoIndex.value = 0
                } else {
                    if (_userSelectedPhoto.value != null && activePhoto != null &&
                        (_userSelectedPhoto.value?.id == activePhoto.id || _userSelectedPhoto.value?.uri == activePhoto.uri)
                    ) {
                        _userSelectedPhoto.value = null
                    }
                    val userPhoto = _userSelectedPhoto.value
                    if (userPhoto != null) {
                        val userIdx = photos.indexOfFirst { it.id == userPhoto.id || it.uri == userPhoto.uri }
                        if (userIdx >= 0) {
                            _selectedPreviewPhotoIndex.value = userIdx
                        } else if (_selectedPreviewPhotoIndex.value >= photos.size) {
                            _selectedPreviewPhotoIndex.value = photos.size - 1
                        }
                    } else if (activePhoto != null && activePhoto.id != lastSeenActivePhotoId) {
                        val activeIdx = photos.indexOfFirst { it.id == activePhoto.id || it.uri == activePhoto.uri }
                        if (activeIdx >= 0) {
                            lastSeenActivePhotoId = activePhoto.id
                            _selectedPreviewPhotoIndex.value = activeIdx
                        } else if (_selectedPreviewPhotoIndex.value >= photos.size) {
                            _selectedPreviewPhotoIndex.value = photos.size - 1
                        }
                    } else if (_selectedPreviewPhotoIndex.value >= photos.size) {
                        _selectedPreviewPhotoIndex.value = photos.size - 1
                    }
                }
            }
        }
    }

    fun selectPreviewPhotoIndex(index: Int) {
        val photos = previewPhotos.value
        if (index in photos.indices) {
            _selectedPreviewPhotoIndex.value = index
            onPhotoSelectedByUser(photos[index])
        }
    }

    fun nextPreviewPhoto() {
        val photos = previewPhotos.value
        if (photos.isNotEmpty()) {
            val nextIdx = (_selectedPreviewPhotoIndex.value + 1) % photos.size
            _selectedPreviewPhotoIndex.value = nextIdx
            onPhotoSelectedByUser(photos[nextIdx])
        }
    }

    fun previousPreviewPhoto() {
        val photos = previewPhotos.value
        if (photos.isNotEmpty()) {
            val prevIdx = (_selectedPreviewPhotoIndex.value - 1 + photos.size) % photos.size
            _selectedPreviewPhotoIndex.value = prevIdx
            onPhotoSelectedByUser(photos[prevIdx])
        }
    }

    private fun onPhotoSelectedByUser(photo: PhotoItem) {
        val active = activeWallpaperPhoto.value
        if (active != null && (photo.id == active.id || photo.uri == active.uri)) {
            // User navigated back to the currently active wallpaper
            _userSelectedPhoto.value = null
            pendingWallpaperPhoto = null
            updateWallpaperDebounceJob?.cancel()
            updateWallpaperDebounceJob = null
            return
        }

        _userSelectedPhoto.value = photo
        pendingWallpaperPhoto = photo

        // Debounce: update the wallpaper sooner (after 1.5s) if user pauses on this photo
        updateWallpaperDebounceJob?.cancel()
        updateWallpaperDebounceJob = viewModelScope.launch {
            delay(WALLPAPER_UPDATE_DEBOUNCE_MILLIS)
            applyPendingWallpaper()
        }
    }

    fun applyPendingWallpaper() {
        val photoToApply = pendingWallpaperPhoto ?: return
        updateWallpaperDebounceJob?.cancel()
        updateWallpaperDebounceJob = null
        pendingWallpaperPhoto = null

        viewModelScope.launch {
            val result = wallpaperRepository.setPhotoAsWallpaper(photoToApply)
            if (result.isSuccess) {
                _userSelectedPhoto.value = null
            }
        }
    }

    fun flushPendingWallpaperUpdate() {
        applyPendingWallpaper()
    }

    fun onAppBackgrounded() {
        updateWallpaperDebounceJob?.cancel()
        updateWallpaperDebounceJob = null

        val photoToApply = pendingWallpaperPhoto ?: run {
            val current = currentPreviewPhoto.value
            val active = activeWallpaperPhoto.value
            if (current != null && (active == null || (current.id != active.id && current.uri != active.uri))) {
                current
            } else {
                null
            }
        }

        if (photoToApply != null) {
            pendingWallpaperPhoto = null
            _userSelectedPhoto.value = null
            viewModelScope.launch {
                wallpaperRepository.setPhotoAsWallpaper(photoToApply)
            }
        } else if (hasPendingCustomization) {
            hasPendingCustomization = false
            viewModelScope.launch {
                wallpaperRepository.reapplyCurrentWallpaper()
            }
        }
    }

    fun togglePhotoSelection(photoId: String, isSelected: Boolean) {
        if (!isSelected) {
            _removedPhotoIds.value = _removedPhotoIds.value + photoId
            if (_userSelectedPhoto.value?.id == photoId) {
                _userSelectedPhoto.value = null
            }
            if (pendingWallpaperPhoto?.id == photoId) {
                pendingWallpaperPhoto = null
                updateWallpaperDebounceJob?.cancel()
                updateWallpaperDebounceJob = null
            }
        } else {
            _removedPhotoIds.value = _removedPhotoIds.value - photoId
        }
        viewModelScope.launch {
            wallpaperRepository.togglePhotoSelection(photoId, isSelected)
        }
    }

    fun removePhotoFromBackgrounds(photoId: String) {
        togglePhotoSelection(photoId, false)
    }

    fun togglePhotoEnabled(photoId: String, isEnabled: Boolean) {
        togglePhotoSelection(photoId, isEnabled)
    }

    fun checkLiveWallpaperStatus() {
        _isLiveWallpaperActive.value = wallpaperRepository.isLiveWallpaperActive()
    }

    fun setPhotoAsWallpaper(photo: PhotoItem) {
        updateWallpaperDebounceJob?.cancel()
        updateWallpaperDebounceJob = null
        pendingWallpaperPhoto = null
        _userSelectedPhoto.value = photo

        val idx = previewPhotos.value.indexOfFirst { it.id == photo.id || it.uri == photo.uri }
        if (idx >= 0) {
            _selectedPreviewPhotoIndex.value = idx
        }

        viewModelScope.launch {
            val isLive = wallpaperRepository.isLiveWallpaperActive()
            val result = wallpaperRepository.setPhotoAsWallpaper(photo)
            if (result.isSuccess) {
                _userSelectedPhoto.value = null
                if (!isLive) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Wallpaper set as static image. Activate Live Wallpaper for double-tap gestures & 10s rotation!"))
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to update wallpaper: $errorMsg"))
            }
        }
    }

    fun openAlbumInPreview(albumId: String, photoId: String? = null) {
        viewModelScope.launch {
            val photos = previewPhotos.value
            val existing = if (photoId != null) {
                photos.firstOrNull { it.id == photoId }
            } else {
                photos.firstOrNull { it.albumId == albumId }
            }

            val targetPhoto = existing ?: if (photoId != null) {
                albumRepository.getPhotosInAlbum(albumId).first().firstOrNull { it.id == photoId }
            } else {
                albumRepository.getPhotosInAlbum(albumId).first().firstOrNull()
            }

            if (targetPhoto != null) {
                _userSelectedPhoto.value = targetPhoto
                val updatedPhotos = previewPhotos.value
                val idx = updatedPhotos.indexOfFirst { it.id == targetPhoto.id }
                if (idx >= 0) {
                    _selectedPreviewPhotoIndex.value = idx
                }
                onPhotoSelectedByUser(targetPhoto)
            }
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
        if (isSelected) {
            _removedPhotoIds.value = emptySet()
        }
        viewModelScope.launch {
            toggleAlbumSelectionUseCase(albumId, isSelected)
        }
    }

    fun updatePhotoCustomization(photoId: String, cropX: Float?, cropY: Float?, zoom: Float?) {
        hasPendingCustomization = true
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

    fun addGoogleDriveFolder(url: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.addGoogleDriveFolder(url)
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

    fun syncGoogleDriveFolder(albumId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.syncGoogleDriveFolder(albumId)
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
