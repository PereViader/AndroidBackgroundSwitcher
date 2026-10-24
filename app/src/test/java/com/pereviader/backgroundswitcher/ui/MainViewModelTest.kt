package com.pereviader.backgroundswitcher.ui

import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.background.WorkScheduler
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetAlbumsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import com.pereviader.backgroundswitcher.domain.usecase.ToggleAlbumSelectionUseCase
import com.pereviader.backgroundswitcher.domain.usecase.UpdateWallpaperSettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getAlbumsUseCase: GetAlbumsUseCase
    private lateinit var toggleAlbumSelectionUseCase: ToggleAlbumSelectionUseCase
    private lateinit var getWallpaperSettingsUseCase: GetWallpaperSettingsUseCase
    private lateinit var updateWallpaperSettingsUseCase: UpdateWallpaperSettingsUseCase
    private lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase
    private lateinit var albumRepository: AlbumRepository
    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var workScheduler: WorkScheduler

    private val activePhotoFlow = MutableStateFlow<PhotoItem?>(null)
    private val selectedPhotosFlow = MutableStateFlow<List<PhotoItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getAlbumsUseCase = mockk(relaxed = true)
        toggleAlbumSelectionUseCase = mockk(relaxed = true)
        getWallpaperSettingsUseCase = mockk(relaxed = true)
        updateWallpaperSettingsUseCase = mockk(relaxed = true)
        cycleWallpaperUseCase = mockk(relaxed = true)
        albumRepository = mockk(relaxed = true)
        wallpaperRepository = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)

        coEvery { getAlbumsUseCase() } returns flowOf(emptyList())
        coEvery { getWallpaperSettingsUseCase() } returns flowOf(WallpaperSettings())
        coEvery { wallpaperRepository.getCurrentPhoto() } returns activePhotoFlow
        coEvery { albumRepository.getPhotosFromSelectedAlbums() } returns selectedPhotosFlow
        coEvery { wallpaperRepository.setPhotoAsWallpaper(any()) } returns Result.success(Unit)
        coEvery { wallpaperRepository.reapplyCurrentWallpaper() } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            getAlbumsUseCase = getAlbumsUseCase,
            toggleAlbumSelectionUseCase = toggleAlbumSelectionUseCase,
            getWallpaperSettingsUseCase = getWallpaperSettingsUseCase,
            updateWallpaperSettingsUseCase = updateWallpaperSettingsUseCase,
            cycleWallpaperUseCase = cycleWallpaperUseCase,
            albumRepository = albumRepository,
            wallpaperRepository = wallpaperRepository,
            workScheduler = workScheduler
        )
    }

    private fun TestScope.subscribeAll(viewModel: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.albums.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settings.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.activeWallpaperPhoto.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.previewPhotos.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.currentPreviewPhoto.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.selectedPreviewPhotoIndex.collect() }
    }

    @Test
    fun `preview page displays the active wallpaper on launch`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")

        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo2

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        // Preview photos should contain both photos
        assertThat(viewModel.previewPhotos.value).containsExactly(photo1, photo2).inOrder()
        // Index should be pointing to photo2 (the active wallpaper)
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p2")
    }

    @Test
    fun `preview page includes and displays active wallpaper even if its album is unselected`() = runTest(testDispatcher) {
        val photoActive = PhotoItem(id = "active_p", albumId = "unselected_album", uri = "content://media/active")
        val photoOther1 = PhotoItem(id = "other_1", albumId = "selected_album", uri = "content://media/other1")
        val photoOther2 = PhotoItem(id = "other_2", albumId = "selected_album", uri = "content://media/other2")

        selectedPhotosFlow.value = listOf(photoOther1, photoOther2)
        activePhotoFlow.value = photoActive

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        // Active wallpaper must be included in previewPhotos
        assertThat(viewModel.previewPhotos.value).contains(photoActive)
        // And the selected index should point to the active wallpaper
        val activeIndex = viewModel.previewPhotos.value.indexOfFirst { it.id == "active_p" }
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(activeIndex)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("active_p")
    }

    @Test
    fun `when user changes image, wallpaper is updated as soon as app is put on background`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")

        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(0)

        // User navigates to next photo (p2)
        viewModel.nextPreviewPhoto()
        advanceTimeBy(100L) // Less than debounce time (1500ms)

        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p2")

        // No wallpaper update should have occurred yet (before debounce or background)
        coVerify(exactly = 0) { wallpaperRepository.setPhotoAsWallpaper(photo2) }

        // User puts app on background
        viewModel.onAppBackgrounded()
        advanceUntilIdle()

        // Wallpaper must be updated to photo2 immediately upon backgrounding
        coVerify(exactly = 1) { wallpaperRepository.setPhotoAsWallpaper(photo2) }
    }

    @Test
    fun `when user pauses on a changed photo, wallpaper is updated sooner via debounce`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")

        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        // User chooses photo2
        viewModel.selectPreviewPhotoIndex(1)
        advanceTimeBy(500L)

        // Not yet updated after 500ms
        coVerify(exactly = 0) { wallpaperRepository.setPhotoAsWallpaper(photo2) }

        // 1500ms debounce fires
        advanceTimeBy(1100L)
        advanceUntilIdle()

        coVerify(exactly = 1) { wallpaperRepository.setPhotoAsWallpaper(photo2) }
    }

    @Test
    fun `when user switches tabs, pending wallpaper update is flushed sooner`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")

        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        viewModel.selectPreviewPhotoIndex(1)
        advanceTimeBy(200L)

        // Tab switch triggers flush
        viewModel.flushPendingWallpaperUpdate()
        advanceUntilIdle()

        coVerify(exactly = 1) { wallpaperRepository.setPhotoAsWallpaper(photo2) }
    }

    @Test
    fun `external wallpaper rotation updates preview to display new active wallpaper`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")

        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p1")

        // External cycle happens (e.g. periodic worker or double-tap)
        activePhotoFlow.value = photo2
        advanceUntilIdle()

        // Preview should follow the newly active wallpaper
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p2")
    }

    @Test
    fun `customizing active wallpaper triggers reapply on background`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        selectedPhotosFlow.value = listOf(photo1)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        viewModel.updatePhotoCustomization("p1", 0.4f, 0.6f, 1.5f)
        viewModel.onAppBackgrounded()
        advanceUntilIdle()

        coVerify(atLeast = 1) { wallpaperRepository.updatePhotoCustomization("p1", 0.4f, 0.6f, 1.5f) }
        coVerify(atLeast = 1) { wallpaperRepository.reapplyCurrentWallpaper() }
    }

    @Test
    fun `togglePhotoSelection delegates to wallpaperRepository`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        viewModel.togglePhotoSelection("p1", false)
        advanceUntilIdle()

        coVerify(exactly = 1) { wallpaperRepository.togglePhotoSelection("p1", false) }
    }

    @Test
    fun `removePhotoFromBackgrounds removes photo from previewPhotos and clamps index`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1", isSelected = true)
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2", isSelected = true)
        val photo3 = PhotoItem(id = "p3", albumId = "a1", uri = "content://media/3", isSelected = true)

        selectedPhotosFlow.value = listOf(photo1, photo2, photo3)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        // Navigate to the last photo (index 2 -> p3)
        viewModel.selectPreviewPhotoIndex(2)
        advanceTimeBy(100L)
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(2)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p3")

        // Remove p3 from backgrounds
        viewModel.removePhotoFromBackgrounds("p3")
        selectedPhotosFlow.value = listOf(photo1, photo2)
        advanceUntilIdle()

        coVerify(exactly = 1) { wallpaperRepository.togglePhotoSelection("p3", false) }
        assertThat(viewModel.previewPhotos.value.map { it.id }).containsExactly("p1", "p2").inOrder()
        // Index should clamp from 2 to 1 (p2)
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p2")
    }

    @Test
    fun `toggleAlbum delegates to toggleAlbumSelectionUseCase`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        viewModel.toggleAlbum("album_123", true)
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleAlbumSelectionUseCase("album_123", true) }
    }

    @Test
    fun `updateSettings delegates to updateWallpaperSettingsUseCase`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        val newSettings = WallpaperSettings(intervalMinutes = 30L)
        viewModel.updateSettings(newSettings)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateWallpaperSettingsUseCase(newSettings) }
    }

    @Test
    fun `navigation wraps around backwards and forwards in filmstrip`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")
        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(0)

        // Previous from index 0 should wrap to index 1 (last)
        viewModel.previousPreviewPhoto()
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)

        // Next from index 1 should wrap to index 0
        viewModel.nextPreviewPhoto()
        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(0)
    }

    @Test
    fun `setPhotoAsWallpaper immediately updates wallpaper, cancels debounce, and emits snackbar`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")
        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns true

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        val emittedEvents = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { emittedEvents.add(it) }
        }
        advanceUntilIdle()

        // Navigate to photo2 (starts debounce timer) then explicitly click "Set as Wallpaper"
        viewModel.selectPreviewPhotoIndex(1)
        viewModel.setPhotoAsWallpaper(photo2)
        advanceUntilIdle()

        // Advance past debounce window to ensure debounce was cancelled and does not fire a second time
        advanceTimeBy(2000L)
        advanceUntilIdle()

        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        assertThat(viewModel.currentPreviewPhoto.value?.id).isEqualTo("p2")
        coVerify(exactly = 1) { wallpaperRepository.setPhotoAsWallpaper(photo2) }
        assertThat(emittedEvents).contains(UiEvent.ShowSnackbar("Wallpaper updated to selected photo!"))
    }

    @Test
    fun `setPhotoAsWallpaper when live wallpaper is inactive emits static wallpaper snackbar`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        val photo2 = PhotoItem(id = "p2", albumId = "a1", uri = "content://media/2")
        selectedPhotosFlow.value = listOf(photo1, photo2)
        activePhotoFlow.value = photo1
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns false

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        val emittedEvents = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { emittedEvents.add(it) }
        }
        advanceUntilIdle()

        viewModel.setPhotoAsWallpaper(photo2)
        advanceUntilIdle()

        assertThat(viewModel.selectedPreviewPhotoIndex.value).isEqualTo(1)
        coVerify(exactly = 1) { wallpaperRepository.setPhotoAsWallpaper(photo2) }
        assertThat(emittedEvents).contains(
            UiEvent.ShowSnackbar("Wallpaper set as static image. Activate Live Wallpaper for double-tap gestures & 10s rotation!")
        )
    }

    @Test
    fun `setPhotoAsWallpaper emits error snackbar when repository fails`() = runTest(testDispatcher) {
        val photo1 = PhotoItem(id = "p1", albumId = "a1", uri = "content://media/1")
        selectedPhotosFlow.value = listOf(photo1)
        coEvery { wallpaperRepository.setPhotoAsWallpaper(photo1) } returns Result.failure(IllegalStateException("Disk read error"))

        val viewModel = createViewModel()
        subscribeAll(viewModel)
        val emittedEvents = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { emittedEvents.add(it) }
        }
        advanceUntilIdle()

        viewModel.setPhotoAsWallpaper(photo1)
        advanceUntilIdle()

        assertThat(emittedEvents).contains(UiEvent.ShowSnackbar("Failed to update wallpaper: Disk read error"))
    }
}
