package com.pereviader.backgroundswitcher.ui.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePhoto1 = PhotoItem(
        id = "p1",
        albumId = "a1",
        uri = "content://media/1",
        width = 1080,
        height = 1920
    )

    private val samplePhoto2 = PhotoItem(
        id = "p2",
        albumId = "a1",
        uri = "content://media/2",
        width = 1080,
        height = 1920
    )

    @Test
    fun `displays photo counter`() {
        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 0,
                currentPhoto = samplePhoto1,
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = {},
                onPreviousPhoto = {},
                onNextPhoto = {},
                onTogglePhotoEnabled = { _, _ -> },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {}
            )
        }

        composeTestRule.onNodeWithText("1 of 2").assertIsDisplayed()
    }

    @Test
    fun `clicking next and previous chevrons fires callbacks`() {
        var nextClicked = false
        var prevClicked = false

        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 0,
                currentPhoto = samplePhoto1,
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = {},
                onPreviousPhoto = { prevClicked = true },
                onNextPhoto = { nextClicked = true },
                onTogglePhotoEnabled = { _, _ -> },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Next Photo").performClick()
        assert(nextClicked)

        composeTestRule.onNodeWithContentDescription("Previous Photo").performClick()
        assert(prevClicked)
    }

    @Test
    fun `clicking thumbnail in filmstrip invokes onSelectPhotoIndex`() {
        var selectedIndex = -1

        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 0,
                currentPhoto = samplePhoto1,
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = { selectedIndex = it },
                onPreviousPhoto = {},
                onNextPhoto = {},
                onTogglePhotoEnabled = { _, _ -> },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {}
            )
        }

        composeTestRule.onNodeWithTag("filmstrip_item_p2").performClick()
        assert(selectedIndex == 1)
    }

    @Test
    fun `long press opens Photo Options modal and clicking Remove from Backgrounds invokes onRemovePhoto callback`() {
        var removedPhotoId: String? = null

        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 1,
                currentPhoto = samplePhoto2,
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = {},
                onPreviousPhoto = {},
                onNextPhoto = {},
                onRemovePhoto = { removedPhotoId = it },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {}
            )
        }

        composeTestRule.onNodeWithTag("photo_preview").performTouchInput {
            longClick()
        }

        composeTestRule.onNodeWithText("Photo Options").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove from Backgrounds").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove from Backgrounds").performClick()
        assert(removedPhotoId == "p2")
    }
}
