package com.pereviader.backgroundswitcher.ui.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun `when current photo is active wallpaper, displays Active Wallpaper badge`() {
        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 0,
                currentPhoto = samplePhoto1,
                activeWallpaperPhotoId = "p1",
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = {},
                onPreviousPhoto = {},
                onNextPhoto = {},
                onTogglePhotoEnabled = { _, _ -> },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {}
            )
        }

        composeTestRule.onNodeWithText("Active Wallpaper").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 2").assertIsDisplayed()
    }

    @Test
    fun `when current photo is not active wallpaper, displays Set as Wallpaper button and click triggers callback`() {
        var wallpaperSetCalled = false
        var selectedPhoto: PhotoItem? = null

        composeTestRule.setContent {
            PreviewScreen(
                photos = listOf(samplePhoto1, samplePhoto2),
                currentIndex = 1,
                currentPhoto = samplePhoto2,
                activeWallpaperPhotoId = "p1",
                isLiveWallpaperActive = true,
                onSelectPhotoIndex = {},
                onPreviousPhoto = {},
                onNextPhoto = {},
                onTogglePhotoEnabled = { _, _ -> },
                onCustomizationChanged = { _, _, _, _ -> },
                onSetAsWallpaper = {
                    wallpaperSetCalled = true
                    selectedPhoto = it
                }
            )
        }

        composeTestRule.onNodeWithText("Set as Wallpaper").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 2").assertIsDisplayed()

        composeTestRule.onNodeWithText("Set as Wallpaper").performClick()
        assert(wallpaperSetCalled)
        assert(selectedPhoto?.id == "p2")
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
                activeWallpaperPhotoId = "p1",
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
}
