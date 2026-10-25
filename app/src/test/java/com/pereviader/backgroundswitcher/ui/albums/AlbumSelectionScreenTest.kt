package com.pereviader.backgroundswitcher.ui.albums

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.Album
import com.pereviader.backgroundswitcher.core.model.AlbumSource
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleAlbum = Album(
        id = "album_1",
        name = "Nature",
        coverUri = "content://media/cover",
        photoCount = 2,
        source = AlbumSource.LOCAL_MEDIASTORE,
        isSelected = false,
        selectedPhotoCount = 0
    )

    private val samplePhoto1 = PhotoItem(
        id = "p1",
        albumId = "album_1",
        uri = "content://media/1",
        isSelected = false
    )

    private val samplePhoto2 = PhotoItem(
        id = "p2",
        albumId = "album_1",
        uri = "content://media/2",
        isSelected = false
    )

    @Test
    fun `opening album opens full screen view with Back, Deselect All, Select All and photo grid, and going back returns to albums page`() {
        val photosFlow = MutableStateFlow(listOf(samplePhoto1, samplePhoto2))
        var bulkSelectedAlbumId: String? = null
        var bulkSelectedState: Boolean? = null
        var toggledPhotoId: String? = null
        var toggledPhotoState: Boolean? = null

        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = listOf(sampleAlbum),
                onToggleAlbum = { albumId, isSelected ->
                    bulkSelectedAlbumId = albumId
                    bulkSelectedState = isSelected
                    photosFlow.value = photosFlow.value.map { it.copy(isSelected = isSelected) }
                },
                onRefresh = {},
                onImportGooglePhotosUrl = {},
                onSyncGooglePhotosAlbum = {},
                onDeleteAlbum = {},
                getPhotosForAlbum = { photosFlow },
                onTogglePhotoSelection = { photoId, isSelected ->
                    toggledPhotoId = photoId
                    toggledPhotoState = isSelected
                    photosFlow.value = photosFlow.value.map {
                        if (it.id == photoId) it.copy(isSelected = isSelected) else it
                    }
                }
            )
        }

        // Initially on albums page
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()

        // Click album card to open full screen album view
        composeTestRule.onNodeWithText("Nature").performClick()

        // Album list header is replaced by full screen album view
        composeTestRule.onNodeWithText("Albums").assertDoesNotExist()
        composeTestRule.onNodeWithTag("album_photos_fullscreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_photos_grid").assertIsDisplayed()

        // Back, Deselect All, and Select All buttons are all displayed at the top
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deselect All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()

        // Click Select All
        composeTestRule.onNodeWithText("Select All").performClick()
        assertThat(bulkSelectedAlbumId).isEqualTo("album_1")
        assertThat(bulkSelectedState).isTrue()

        // Both Select All and Deselect All remain displayed
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deselect All").assertIsDisplayed()

        // Click Deselect All
        composeTestRule.onNodeWithText("Deselect All").performClick()
        assertThat(bulkSelectedAlbumId).isEqualTo("album_1")
        assertThat(bulkSelectedState).isFalse()

        // Select All again, then untoggle individual photo p1
        composeTestRule.onNodeWithText("Select All").performClick()
        composeTestRule.onNodeWithTag("photo_toggle_p1").performClick()
        assertThat(toggledPhotoId).isEqualTo("p1")
        assertThat(toggledPhotoState).isFalse()

        // Both buttons remain displayed when partially selected
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deselect All").assertIsDisplayed()

        // Going back brings back to the album page
        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.onNodeWithTag("album_photos_fullscreen").assertDoesNotExist()
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nature").assertIsDisplayed()
    }

    @Test
    fun `renders Google Drive badge and sync action for GOOGLE_DRIVE_FOLDER album card`() {
        val driveAlbum = Album(
            id = "gd_folder1",
            name = "Drive Landscapes",
            coverUri = "https://lh3.googleusercontent.com/d/f1=w800",
            photoCount = 5,
            source = AlbumSource.GOOGLE_DRIVE_FOLDER,
            isSelected = true,
            selectedPhotoCount = 5,
            sourceUrl = "content://com.google.android.apps.docs.storage/tree/folder1"
        )

        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = listOf(driveAlbum),
                onToggleAlbum = { _, _ -> },
                onRefresh = {},
                onDeleteAlbum = {},
                getPhotosForAlbum = { MutableStateFlow(emptyList()) }
            )
        }

        composeTestRule.onNodeWithText("Drive Landscapes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Drive").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_source_badge_gd_folder1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_sync_gd_folder1").assertIsDisplayed()
    }

    @Test
    fun `renders Google Photos badge for GOOGLE_PHOTOS_PICKER album card`() {
        val photosAlbum = Album(
            id = "google_photos_picker",
            name = "Google Photos",
            coverUri = "content://media/picker/0/101",
            photoCount = 10,
            source = AlbumSource.GOOGLE_PHOTOS_PICKER,
            isSelected = true,
            selectedPhotoCount = 10
        )

        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = listOf(photosAlbum),
                onToggleAlbum = { _, _ -> },
                onRefresh = {},
                onDeleteAlbum = {},
                getPhotosForAlbum = { MutableStateFlow(emptyList()) }
            )
        }

        composeTestRule.onNodeWithText("Google Photos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_source_badge_google_photos_picker", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `clicking Add FAB displays action sheet with Google Drive and Google Photos options`() {
        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = emptyList(),
                onToggleAlbum = { _, _ -> },
                onRefresh = {},
                onDeleteAlbum = {},
                getPhotosForAlbum = { MutableStateFlow(emptyList()) }
            )
        }

        composeTestRule.onNodeWithTag("add_album_fab").performClick()

        composeTestRule.onNodeWithText("Add Wallpapers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Google Drive Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick from Google Photos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_google_drive_option").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pick_google_photos_option").assertIsDisplayed()
    }

    @Test
    fun `working list chip bar switches active list and Assign to Lists button assigns album to multiple lists`() {
        val defaultList = com.pereviader.backgroundswitcher.core.model.WallpaperList(
            id = "default_list",
            name = "My Wallpapers",
            isDefault = true,
            photoCount = 0
        )
        val workList = com.pereviader.backgroundswitcher.core.model.WallpaperList(
            id = "list_work",
            name = "Work Focus",
            isDefault = false,
            photoCount = 0
        )
        val photosFlow = MutableStateFlow(listOf(samplePhoto1, samplePhoto2))
        var selectedWorkingListId = "default_list"
        var assignedAlbumId: String? = null
        var assignedListIds: Set<String>? = null

        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = listOf(sampleAlbum),
                onToggleAlbum = { _, _ -> },
                onRefresh = {},
                onDeleteAlbum = {},
                getPhotosForAlbum = { photosFlow },
                lists = listOf(defaultList, workList),
                workingListId = selectedWorkingListId,
                onSelectWorkingList = { selectedWorkingListId = it },
                onSetAlbumLists = { albumId, listIds ->
                    assignedAlbumId = albumId
                    assignedListIds = listIds
                }
            )
        }

        // Open Nature album
        composeTestRule.onNodeWithText("Nature").performClick()

        // Working list dropdown can be opened and list chosen
        composeTestRule.onNodeWithTag("working_list_dropdown_button").performClick()
        composeTestRule.onNodeWithTag("working_list_option_list_work").performClick()
        assertThat(selectedWorkingListId).isEqualTo("list_work")

        // Click "Assign to Lists..." button and select Work Focus in dialog
        composeTestRule.onNodeWithTag("assign_album_lists_button").performClick()
        composeTestRule.onNodeWithTag("assign_list_row_list_work").performClick()
        composeTestRule.onNodeWithTag("assign_lists_save_button").performClick()

        assertThat(assignedAlbumId).isEqualTo("album_1")
        assertThat(assignedListIds).contains("list_work")
    }

    @Test
    fun `albums header displays title at top, removes tap description, and shows dropdown and refresh button sharing line`() {
        val defaultList = com.pereviader.backgroundswitcher.core.model.WallpaperList(
            id = "default_list",
            name = "My Wallpapers",
            isDefault = true
        )
        val workList = com.pereviader.backgroundswitcher.core.model.WallpaperList(
            id = "list_work",
            name = "Work Focus",
            isDefault = false
        )
        var selectedList = "default_list"
        var refreshClicked = false

        composeTestRule.setContent {
            AlbumSelectionScreen(
                albums = listOf(sampleAlbum),
                onToggleAlbum = { _, _ -> },
                onRefresh = { refreshClicked = true },
                onDeleteAlbum = {},
                getPhotosForAlbum = { MutableStateFlow(emptyList()) },
                lists = listOf(defaultList, workList),
                workingListId = selectedList,
                onSelectWorkingList = { selectedList = it }
            )
        }

        // Title is displayed
        composeTestRule.onNodeWithTag("albums_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()

        // Description element is removed
        composeTestRule.onNodeWithText("Tap an album to view photos • Checkbox selects for cycling").assertDoesNotExist()

        // Dropdown and refresh button are displayed
        composeTestRule.onNodeWithTag("working_list_dropdown_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_albums_button").assertIsDisplayed()

        // Refresh triggers callback
        composeTestRule.onNodeWithTag("refresh_albums_button").performClick()
        assertThat(refreshClicked).isTrue()

        // Dropdown changes selected list
        composeTestRule.onNodeWithTag("working_list_dropdown_button").performClick()
        composeTestRule.onNodeWithTag("working_list_option_list_work").performClick()
        assertThat(selectedList).isEqualTo("list_work")
    }
}
