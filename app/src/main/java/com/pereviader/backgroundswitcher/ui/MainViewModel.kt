package com.pereviader.backgroundswitcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pereviader.backgroundswitcher.background.WorkScheduler
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetAlbumsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.ToggleAlbumSelectionUseCase
import com.pereviader.backgroundswitcher.domain.usecase.UpdateWallpaperSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val workScheduler: WorkScheduler
) : ViewModel() {

    val albums: StateFlow<List<Album>> = getAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<WallpaperSettings> = getWallpaperSettingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WallpaperSettings())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    init {
        refreshAlbums()
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
