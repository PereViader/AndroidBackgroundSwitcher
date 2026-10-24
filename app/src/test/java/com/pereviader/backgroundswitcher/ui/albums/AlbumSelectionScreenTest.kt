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
    fun `opening album dialog shows Select All and allows bulk selection and individual photo toggling`() {
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

        // Click album card to open dialog
        composeTestRule.onNodeWithText("Nature").performClick()

        // Initially 0 / 2 selected, button shows "Select All"
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select All").performClick()

        assertThat(bulkSelectedAlbumId).isEqualTo("album_1")
        assertThat(bulkSelectedState).isTrue()

        // Now all photos are selected, button changes to "Deselect All"
        composeTestRule.onNodeWithText("Deselect All").assertIsDisplayed()

        // Untoggle individual photo p1 after selecting all
        composeTestRule.onNodeWithTag("photo_toggle_p1").performClick()
        assertThat(toggledPhotoId).isEqualTo("p1")
        assertThat(toggledPhotoState).isFalse()

        // Because p1 was untoggled, not all photos are selected, so "Select All" is displayed again
        composeTestRule.onNodeWithText("Select All").assertIsDisplayed()
    }
}
