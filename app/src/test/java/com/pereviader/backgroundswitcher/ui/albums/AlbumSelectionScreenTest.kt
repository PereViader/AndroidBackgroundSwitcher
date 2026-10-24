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
        composeTestRule.onNodeWithText("Albums (1)").assertIsDisplayed()

        // Click album card to open full screen album view
        composeTestRule.onNodeWithText("Nature").performClick()

        // Album list header is replaced by full screen album view
        composeTestRule.onNodeWithText("Albums (1)").assertDoesNotExist()
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
        composeTestRule.onNodeWithText("Albums (1)").assertIsDisplayed()
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
}
