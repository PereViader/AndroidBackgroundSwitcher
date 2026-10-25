package com.pereviader.backgroundswitcher.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pereviader.backgroundswitcher.core.wallpaper.LiveWallpaperService
import com.pereviader.backgroundswitcher.data.mediastore.MediaStoreContentObserver
import com.pereviader.backgroundswitcher.ui.albums.AlbumSelectionScreen
import com.pereviader.backgroundswitcher.ui.lists.ListsAndScheduleScreen
import com.pereviader.backgroundswitcher.ui.preview.PreviewScreen
import com.pereviader.backgroundswitcher.ui.settings.SettingsScreen
import com.pereviader.backgroundswitcher.ui.theme.BackgroundSwitcherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var mediaStoreObserver: MediaStoreContentObserver

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.refreshAlbums()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        // Register content observer for automatic new photo detection
        mediaStoreObserver.register {
            viewModel.refreshAlbums()
        }

        setContent {
            BackgroundSwitcherTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkLiveWallpaperStatus()
        viewModel.refreshScheduleClock()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackgrounded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaStoreObserver.unregister()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val workingListId by viewModel.workingListId.collectAsState()
    val previewListId by viewModel.previewListId.collectAsState()
    val rulesShared by viewModel.scheduleRulesShared.collectAsState()
    val rulesHome by viewModel.scheduleRulesHome.collectAsState()
    val rulesLock by viewModel.scheduleRulesLock.collectAsState()
    val resolutionShared by viewModel.activeResolutionShared.collectAsState()
    val resolutionHome by viewModel.activeResolutionHome.collectAsState()
    val resolutionLock by viewModel.activeResolutionLock.collectAsState()
    val previewPhotos by viewModel.previewPhotos.collectAsState()
    val selectedPhotoIndex by viewModel.selectedPreviewPhotoIndex.collectAsState()
    val currentPhoto by viewModel.currentPreviewPhoto.collectAsState()
    val isLiveWallpaperActive by viewModel.isLiveWallpaperActive.collectAsState()

    fun openLiveWallpaperChooser() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, LiveWallpaperService::class.java)
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Preview, contentDescription = "Preview") },
                    label = { Text("Preview") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.flushPendingWallpaperUpdate()
                        }
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Albums") },
                    label = { Text("Albums") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.flushPendingWallpaperUpdate()
                        }
                        selectedTab = 2
                    },
                    icon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = "Lists & Schedule") },
                    label = { Text("Lists & Schedule") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.flushPendingWallpaperUpdate()
                        }
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> PreviewScreen(
                photos = previewPhotos,
                currentIndex = selectedPhotoIndex,
                currentPhoto = currentPhoto,
                isLiveWallpaperActive = isLiveWallpaperActive,
                onSelectPhotoIndex = { index -> viewModel.selectPreviewPhotoIndex(index) },
                onPreviousPhoto = { viewModel.previousPreviewPhoto() },
                onNextPhoto = { viewModel.nextPreviewPhoto() },
                onTogglePhotoEnabled = { photoId, isEnabled -> viewModel.togglePhotoSelection(photoId, isEnabled) },
                onRemovePhoto = { photoId -> viewModel.removePhotoFromBackgrounds(photoId) },
                onCustomizationChanged = { photoId, cropX, cropY, zoom ->
                    viewModel.updatePhotoCustomization(photoId, cropX, cropY, zoom)
                },
                onSetAsWallpaper = { photo -> viewModel.setPhotoAsWallpaper(photo) },
                onActivateLiveWallpaper = ::openLiveWallpaperChooser,
                lists = lists,
                previewListId = previewListId,
                activeScheduledListId = if (settings.isSeparateScreensMode) {
                    resolutionHome.activeListId
                } else {
                    resolutionShared.activeListId
                },
                onSelectPreviewList = { listId -> viewModel.selectPreviewList(listId) },
                onSetPhotoLists = { photoId, targetIds -> viewModel.setPhotoLists(photoId, targetIds) },
                onCreateList = { name, onCreated -> viewModel.createList(name, onCreated) },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> AlbumSelectionScreen(
                albums = albums,
                onToggleAlbum = { id, checked -> viewModel.toggleAlbum(id, checked) },
                onRefresh = { viewModel.refreshAlbums() },
                onImportGoogleDriveFolder = { uri -> viewModel.importGoogleDriveFolder(uri) },
                onImportGooglePhotos = { uris -> viewModel.importGooglePhotosFromPicker(uris) },
                onSyncAlbum = { id -> viewModel.syncGoogleDriveFolder(id) },
                onDeleteAlbum = { id -> viewModel.deleteAlbum(id) },
                getPhotosForAlbum = { id -> viewModel.getPhotosForAlbum(id) },
                onTogglePhotoSelection = { photoId, isSelected ->
                    viewModel.togglePhotoSelection(photoId, isSelected)
                },
                onOpenAlbumInPreview = { albumId, photoId ->
                    viewModel.openAlbumInPreview(albumId, photoId)
                    selectedTab = 0
                },
                lists = lists,
                workingListId = workingListId,
                onSelectWorkingList = { listId -> viewModel.selectWorkingList(listId) },
                onSetPhotoLists = { photoId, targetIds -> viewModel.setPhotoLists(photoId, targetIds) },
                onSetAlbumLists = { albumId, targetIds -> viewModel.setAlbumLists(albumId, targetIds) },
                onCreateList = { name, onCreated -> viewModel.createList(name, onCreated) },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> ListsAndScheduleScreen(
                lists = lists,
                isSeparateScreensMode = settings.isSeparateScreensMode,
                rulesShared = rulesShared,
                rulesHome = rulesHome,
                rulesLock = rulesLock,
                resolutionShared = resolutionShared,
                resolutionHome = resolutionHome,
                resolutionLock = resolutionLock,
                fallbackListIdShared = settings.fallbackListIdShared,
                fallbackListIdHome = settings.fallbackListIdHome,
                fallbackListIdLock = settings.fallbackListIdLock,
                observePhotosInList = { listId -> viewModel.observePhotosInList(listId) },
                onCreateList = { name -> viewModel.createList(name) },
                onRenameList = { listId, newName -> viewModel.renameList(listId, newName) },
                onUpdateList = { listId, newName, screen, order -> viewModel.updateListDetails(listId, newName, screen, order) },
                onCreateListWithDetails = { name, screen, order -> viewModel.createList(name, screen, order) },
                onDeleteList = { listId -> viewModel.deleteList(listId) },
                onRemovePhotoFromList = { listId, photoId ->
                    viewModel.removePhotosFromList(listId, listOf(photoId))
                },
                onToggleSeparateScreensMode = { enabled -> viewModel.setSeparateScreensMode(enabled) },
                onSaveRule = { rule -> viewModel.saveScheduleRule(rule) },
                onDeleteRule = { ruleId -> viewModel.deleteScheduleRule(ruleId) },
                onToggleRuleEnabled = { ruleId, enabled -> viewModel.toggleScheduleRuleEnabled(ruleId, enabled) },
                onMoveRulePriority = { scope, ruleId, moveUp ->
                    viewModel.moveScheduleRulePriority(scope, ruleId, moveUp)
                },
                onReorderRules = { scope, orderedIds ->
                    viewModel.reorderScheduleRules(scope, orderedIds)
                },
                onSetFallbackList = { scope, listId -> viewModel.setFallbackList(scope, listId) },
                onCopyScheduleScope = { from, to -> viewModel.copyScheduleScope(from, to) },
                onRefreshClock = { viewModel.refreshScheduleClock() },
                onOpenAddPhotosFromAlbums = { listId ->
                    viewModel.selectWorkingList(listId)
                    selectedTab = 1
                },
                onOpenListInPreview = { listId ->
                    viewModel.selectPreviewList(listId)
                    selectedTab = 0
                },
                modifier = Modifier.padding(innerPadding)
            )
            3 -> SettingsScreen(
                settings = settings,
                isLiveWallpaperActive = isLiveWallpaperActive,
                onUpdateSettings = { newSettings -> viewModel.updateSettings(newSettings) },
                onActivateLiveWallpaper = ::openLiveWallpaperChooser,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
