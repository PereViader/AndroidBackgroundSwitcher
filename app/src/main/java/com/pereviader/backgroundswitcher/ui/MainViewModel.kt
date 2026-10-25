package com.pereviader.backgroundswitcher.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pereviader.backgroundswitcher.background.WorkScheduler
import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperListRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.scheduler.ScheduleEvaluator
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetAlbumsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.ToggleAlbumSelectionUseCase
import com.pereviader.backgroundswitcher.domain.usecase.UpdateWallpaperSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val toggleAlbumSelectionUseCase: ToggleAlbumSelectionUseCase,
    private val getWallpaperSettingsUseCase: GetWallpaperSettingsUseCase,
    private val updateWallpaperSettingsUseCase: UpdateWallpaperSettingsUseCase,
    private val cycleWallpaperUseCase: CycleWallpaperUseCase,
    private val albumRepository: AlbumRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val workScheduler: WorkScheduler,
    private val wallpaperListRepository: WallpaperListRepository? = null,
    private val scheduleRepository: ScheduleRepository? = null,
    private val scheduleEvaluator: ScheduleEvaluator = ScheduleEvaluator()
) : ViewModel() {

    companion object {
        private const val WALLPAPER_UPDATE_DEBOUNCE_MILLIS = 1500L
    }

    private val _workingListId = MutableStateFlow(WallpaperList.DEFAULT_LIST_ID)
    val workingListId: StateFlow<String> = _workingListId.asStateFlow()

    val albums: StateFlow<List<Album>> = _workingListId
        .flatMapLatest { listId -> getAlbumsUseCase(listId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<WallpaperSettings> = getWallpaperSettingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WallpaperSettings())

    val lists: StateFlow<List<WallpaperList>> = (
        wallpaperListRepository?.observeLists()
            ?: flowOf(
                listOf(
                    WallpaperList(
                        id = WallpaperList.DEFAULT_LIST_ID,
                        name = WallpaperList.DEFAULT_LIST_NAME,
                        isDefault = true
                    )
                )
            )
        ).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf(
                WallpaperList(
                    id = WallpaperList.DEFAULT_LIST_ID,
                    name = WallpaperList.DEFAULT_LIST_NAME,
                    isDefault = true
                )
            )
        )

    private val _previewListId = MutableStateFlow<String?>(null)
    val previewListId: StateFlow<String?> = _previewListId.asStateFlow()

    val scheduleRulesShared: StateFlow<List<ScheduleRule>> = (
        scheduleRepository?.observeRulesByScope(ScheduleScreenScope.SHARED) ?: flowOf(emptyList())
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleRulesHome: StateFlow<List<ScheduleRule>> = (
        scheduleRepository?.observeRulesByScope(ScheduleScreenScope.HOME) ?: flowOf(emptyList())
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleRulesLock: StateFlow<List<ScheduleRule>> = (
        scheduleRepository?.observeRulesByScope(ScheduleScreenScope.LOCK) ?: flowOf(emptyList())
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _clockTick = MutableStateFlow(LocalDateTime.now())

    val activeResolutionShared: StateFlow<ActiveScheduleResolution> = combine(
        scheduleRulesShared,
        settings,
        lists,
        _clockTick
    ) { rules, currentSettings, allLists, now ->
        val nonEmpty = allLists.filter { it.photoCount > 0 }.map { it.id }.toSet().ifEmpty { null }
        scheduleEvaluator.evaluateActiveList(
            rules = rules,
            screenScope = ScheduleScreenScope.SHARED,
            fallbackListId = currentSettings.fallbackListIdShared,
            dateTime = now,
            nonEmptyListIds = nonEmpty
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ActiveScheduleResolution(
            screenScope = ScheduleScreenScope.SHARED,
            activeListId = WallpaperList.DEFAULT_LIST_ID,
            matchedRuleId = null,
            isFallback = true
        )
    )

    val activeResolutionHome: StateFlow<ActiveScheduleResolution> = combine(
        scheduleRulesHome,
        settings,
        lists,
        _clockTick
    ) { rules, currentSettings, allLists, now ->
        val nonEmpty = allLists.filter { it.photoCount > 0 }.map { it.id }.toSet().ifEmpty { null }
        scheduleEvaluator.evaluateActiveList(
            rules = rules,
            screenScope = ScheduleScreenScope.HOME,
            fallbackListId = currentSettings.fallbackListIdHome,
            dateTime = now,
            nonEmptyListIds = nonEmpty
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ActiveScheduleResolution(
            screenScope = ScheduleScreenScope.HOME,
            activeListId = WallpaperList.DEFAULT_LIST_ID,
            matchedRuleId = null,
            isFallback = true
        )
    )

    val activeResolutionLock: StateFlow<ActiveScheduleResolution> = combine(
        scheduleRulesLock,
        settings,
        lists,
        _clockTick
    ) { rules, currentSettings, allLists, now ->
        val nonEmpty = allLists.filter { it.photoCount > 0 }.map { it.id }.toSet().ifEmpty { null }
        scheduleEvaluator.evaluateActiveList(
            rules = rules,
            screenScope = ScheduleScreenScope.LOCK,
            fallbackListId = currentSettings.fallbackListIdLock,
            dateTime = now,
            nonEmptyListIds = nonEmpty
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ActiveScheduleResolution(
            screenScope = ScheduleScreenScope.LOCK,
            activeListId = WallpaperList.DEFAULT_LIST_ID,
            matchedRuleId = null,
            isFallback = true
        )
    )

    val activeWallpaperPhoto: StateFlow<PhotoItem?> = wallpaperRepository.getCurrentPhoto()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val photosFromSelectedAlbums: Flow<List<PhotoItem>> =
        albumRepository.getPhotosFromSelectedAlbums()

    private val effectivePreviewSourcePhotos: Flow<List<PhotoItem>> = if (wallpaperListRepository != null) {
        combine(
            _previewListId,
            settings,
            activeResolutionShared,
            activeResolutionHome
        ) { explicitListId, currentSettings, sharedRes, homeRes ->
            explicitListId ?: if (currentSettings.isSeparateScreensMode) {
                homeRes.activeListId
            } else {
                sharedRes.activeListId
            }
        }.flatMapLatest { targetListId ->
            combine(
                wallpaperListRepository.observePhotosInList(targetListId),
                photosFromSelectedAlbums
            ) { listPhotos, fallbackSelectedPhotos ->
                if (listPhotos.isNotEmpty() || _previewListId.value != null) {
                    listPhotos
                } else {
                    fallbackSelectedPhotos
                }
            }
        }
    } else {
        photosFromSelectedAlbums
    }

    // Holds a user-selected photo from browsing (e.g. albums dialog or filmstrip)
    private val _userSelectedPhoto = MutableStateFlow<PhotoItem?>(null)
    val userSelectedPhoto: StateFlow<PhotoItem?> = _userSelectedPhoto.asStateFlow()

    private val _removedPhotoIds = MutableStateFlow<Set<String>>(emptySet())

    // The list of preview photos always ensures the currently active wallpaper photo is included
    // so the user is always able to inspect and edit its framing immediately, along with selected photos.
    val previewPhotos: StateFlow<List<PhotoItem>> = combine(
        effectivePreviewSourcePhotos,
        activeWallpaperPhoto,
        _userSelectedPhoto,
        _removedPhotoIds
    ) { selectedPhotos, activePhoto, userPhoto, removedIds ->
        val result = mutableListOf<PhotoItem>()
        if (activePhoto != null &&
            activePhoto.id !in removedIds &&
            _previewListId.value == null &&
            selectedPhotos.none { it.id == activePhoto.id || it.uri == activePhoto.uri }
        ) {
            result.add(activePhoto)
        }
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
            wallpaperListRepository?.ensureDefaultListExists()
            val currentSettings = getWallpaperSettingsUseCase().first()
            if (currentSettings.isEnabled) {
                workScheduler.scheduleWallpaperCycling(
                    currentSettings.intervalMinutes,
                    currentSettings.onlyOnWifi
                )
                workScheduler.schedulePeriodicSync(currentSettings.onlyOnWifi)
                rescheduleNextBoundaryWorker()
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
                    } else if (activePhoto != null && activePhoto.id != lastSeenActivePhotoId && _previewListId.value == null) {
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

    fun refreshScheduleClock(dateTime: LocalDateTime = LocalDateTime.now()) {
        _clockTick.value = dateTime
    }

    private suspend fun rescheduleNextBoundaryWorker() {
        val nextBoundary = scheduleRepository?.findNextBoundaryTransition()
        workScheduler.scheduleNextBoundaryTransition(nextBoundary?.delayMillis)
    }

    // --- Photo Lists Management ---

    fun selectWorkingList(listId: String) {
        _workingListId.value = listId
    }

    fun selectPreviewList(listId: String?) {
        _previewListId.value = listId
        _selectedPreviewPhotoIndex.value = 0
    }

    fun observePhotosInList(listId: String): Flow<List<PhotoItem>> {
        return wallpaperListRepository?.observePhotosInList(listId) ?: flowOf(emptyList())
    }

    fun createList(name: String, onCreated: (WallpaperList) -> Unit) {
        createList(name, TargetScreen.BOTH, RotationOrder.LEAST_RECENTLY_USED, onCreated)
    }

    fun createList(
        name: String,
        targetScreen: TargetScreen = TargetScreen.BOTH,
        rotationOrder: RotationOrder = RotationOrder.LEAST_RECENTLY_USED,
        onCreated: (WallpaperList) -> Unit = {}
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val created = wallpaperListRepository?.createList(trimmed, targetScreen, rotationOrder)
            if (created != null) {
                _workingListId.value = created.id
                onCreated(created)
                _eventFlow.emit(UiEvent.ShowSnackbar("Created list '${created.name}'"))
            }
        }
    }

    fun updateListDetails(
        listId: String,
        newName: String,
        targetScreen: TargetScreen,
        rotationOrder: RotationOrder
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            wallpaperListRepository?.updateList(listId, trimmed, targetScreen, rotationOrder)
            _eventFlow.emit(UiEvent.ShowSnackbar("Updated list '$trimmed'"))
        }
    }

    fun renameList(listId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            wallpaperListRepository?.renameList(listId, trimmed)
            _eventFlow.emit(UiEvent.ShowSnackbar("Renamed list to '$trimmed'"))
        }
    }

    fun deleteList(listId: String) {
        if (listId == WallpaperList.DEFAULT_LIST_ID) return
        viewModelScope.launch {
            val deleted = wallpaperListRepository?.deleteList(listId) == true
            if (deleted) {
                if (_workingListId.value == listId) {
                    _workingListId.value = WallpaperList.DEFAULT_LIST_ID
                }
                if (_previewListId.value == listId) {
                    _previewListId.value = null
                }
                rescheduleNextBoundaryWorker()
                _eventFlow.emit(UiEvent.ShowSnackbar("List deleted"))
            }
        }
    }

    fun assignPhotosToList(listId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        _removedPhotoIds.value = _removedPhotoIds.value - photoIds.toSet()
        viewModelScope.launch {
            wallpaperListRepository?.assignPhotosToList(listId, photoIds)
            val listName = lists.value.find { it.id == listId }?.name ?: "list"
            _eventFlow.emit(UiEvent.ShowSnackbar("Added ${photoIds.size} photo(s) to '$listName'"))
        }
    }

    fun removePhotosFromList(listId: String, photoIds: List<String>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            wallpaperListRepository?.removePhotosFromList(listId, photoIds)
            val listName = lists.value.find { it.id == listId }?.name ?: "list"
            _eventFlow.emit(UiEvent.ShowSnackbar("Removed ${photoIds.size} photo(s) from '$listName'"))
        }
    }

    fun setPhotoLists(photoId: String, targetListIds: Set<String>) {
        if (targetListIds.isNotEmpty()) {
            _removedPhotoIds.value = _removedPhotoIds.value - photoId
        }
        viewModelScope.launch {
            if (wallpaperListRepository != null) {
                wallpaperListRepository.setPhotoLists(photoId, targetListIds)
                _eventFlow.emit(UiEvent.ShowSnackbar("Updated list assignments"))
            } else {
                wallpaperRepository.togglePhotoSelection(photoId, targetListIds.isNotEmpty())
            }
        }
    }

    fun setAlbumLists(albumId: String, targetListIds: Set<String>) {
        if (targetListIds.isNotEmpty()) {
            _removedPhotoIds.value = emptySet()
        }
        viewModelScope.launch {
            if (wallpaperListRepository != null) {
                wallpaperListRepository.setAlbumLists(albumId, targetListIds)
                _eventFlow.emit(UiEvent.ShowSnackbar("Updated album list assignments"))
            } else {
                toggleAlbumSelectionUseCase(albumId, targetListIds.isNotEmpty())
            }
        }
    }

    // --- Schedule Management ---

    fun saveScheduleRule(rule: ScheduleRule) {
        viewModelScope.launch {
            scheduleRepository?.saveRule(rule)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
            _eventFlow.emit(UiEvent.ShowSnackbar("Schedule rule saved"))
        }
    }

    fun deleteScheduleRule(ruleId: String) {
        viewModelScope.launch {
            scheduleRepository?.deleteRule(ruleId)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
            _eventFlow.emit(UiEvent.ShowSnackbar("Schedule rule removed"))
        }
    }

    fun toggleScheduleRuleEnabled(ruleId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            scheduleRepository?.toggleRuleEnabled(ruleId, isEnabled)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
        }
    }

    fun moveScheduleRulePriority(scope: ScheduleScreenScope, ruleId: String, moveUp: Boolean) {
        viewModelScope.launch {
            scheduleRepository?.moveRulePriority(scope, ruleId, moveUp)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
        }
    }

    fun reorderScheduleRules(scope: ScheduleScreenScope, orderedRuleIds: List<String>) {
        viewModelScope.launch {
            scheduleRepository?.reorderRules(scope, orderedRuleIds)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
        }
    }

    fun setSeparateScreensMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                scheduleRepository?.initializeSeparateSchedulesFromSharedIfNeeded()
            }
            val current = settings.value
            updateSettings(current.copy(isSeparateScreensMode = enabled))
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
        }
    }

    fun setFallbackList(scope: ScheduleScreenScope, listId: String) {
        viewModelScope.launch {
            val current = settings.value
            val updated = when (scope) {
                ScheduleScreenScope.SHARED -> current.copy(fallbackListIdShared = listId)
                ScheduleScreenScope.HOME -> current.copy(fallbackListIdHome = listId)
                ScheduleScreenScope.LOCK -> current.copy(fallbackListIdLock = listId)
            }
            updateSettings(updated)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
        }
    }

    fun copyScheduleScope(fromScope: ScheduleScreenScope, toScope: ScheduleScreenScope) {
        viewModelScope.launch {
            scheduleRepository?.copyScheduleScope(fromScope, toScope)
            refreshScheduleClock()
            rescheduleNextBoundaryWorker()
            val fromLabel = if (fromScope == ScheduleScreenScope.HOME) "Home" else "Lock"
            val toLabel = if (toScope == ScheduleScreenScope.HOME) "Home" else "Lock"
            _eventFlow.emit(UiEvent.ShowSnackbar("Copied $fromLabel schedule to $toLabel"))
        }
    }

    // --- Preview & Existing Actions ---

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
            _userSelectedPhoto.value = null
            pendingWallpaperPhoto = null
            updateWallpaperDebounceJob?.cancel()
            updateWallpaperDebounceJob = null
            return
        }

        _userSelectedPhoto.value = photo
        pendingWallpaperPhoto = photo

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
            val targetListId = _workingListId.value
            if (wallpaperListRepository != null && targetListId != WallpaperList.DEFAULT_LIST_ID) {
                wallpaperListRepository.togglePhotoInList(targetListId, photoId, isSelected)
            } else {
                wallpaperRepository.togglePhotoSelection(photoId, isSelected)
                wallpaperListRepository?.togglePhotoInList(WallpaperList.DEFAULT_LIST_ID, photoId, isSelected)
            }
        }
    }

    fun removePhotoFromBackgrounds(photoId: String) {
        val currentPreviewList = _previewListId.value
        if (currentPreviewList != null && wallpaperListRepository != null) {
            removePhotosFromList(currentPreviewList, listOf(photoId))
        } else {
            togglePhotoSelection(photoId, false)
        }
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
            val targetListId = _workingListId.value
            if (wallpaperListRepository != null && targetListId != WallpaperList.DEFAULT_LIST_ID) {
                if (isSelected) {
                    wallpaperListRepository.assignAlbumToList(albumId, targetListId)
                } else {
                    wallpaperListRepository.removeAlbumFromList(albumId, targetListId)
                }
            } else {
                toggleAlbumSelectionUseCase(albumId, isSelected)
                if (wallpaperListRepository != null) {
                    if (isSelected) {
                        wallpaperListRepository.assignAlbumToList(albumId, WallpaperList.DEFAULT_LIST_ID)
                    } else {
                        wallpaperListRepository.removeAlbumFromList(albumId, WallpaperList.DEFAULT_LIST_ID)
                    }
                }
            }
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
                rescheduleNextBoundaryWorker()
            } else {
                workScheduler.cancelAll()
            }
        }
    }

    fun triggerImmediateCycle() {
        viewModelScope.launch {
            val result = cycleWallpaperUseCase(forceLiveUpdate = true)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Wallpaper updated successfully!"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to update wallpaper: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun importGoogleDriveFolder(treeUri: Uri, folderName: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.importGoogleDriveFolder(treeUri, folderName)
            _isRefreshing.value = false
            if (result.isSuccess) {
                val album = result.getOrNull()
                _eventFlow.emit(UiEvent.ShowSnackbar("Imported '${album?.name}' (${album?.photoCount} photos)"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to import Google Drive folder: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun importGooglePhotosFromPicker(uris: List<Uri>, albumName: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = albumRepository.importGooglePhotosFromPicker(uris.map { it.toString() }, albumName)
            _isRefreshing.value = false
            if (result.isSuccess) {
                val album = result.getOrNull()
                _eventFlow.emit(UiEvent.ShowSnackbar("Imported ${album?.photoCount} photos from Google Photos"))
            } else {
                _eventFlow.emit(UiEvent.ShowSnackbar("Failed to import photos: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun syncGooglePhotosAlbum(albumId: String) {
        syncGoogleDriveFolder(albumId)
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
