package com.pereviader.backgroundswitcher.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultSettings = WallpaperSettings(
        isEnabled = true,
        intervalSeconds = 900L,
        intervalMinutes = 15L,
        cycleOnUnlock = true,
        doubleTapToCycle = true
    )

    @Test
    fun `when live wallpaper is active, shows active text, no button, and no double-tap description`() {
        composeTestRule.setContent {
            SettingsScreen(
                settings = defaultSettings,
                isLiveWallpaperActive = true,
                onUpdateSettings = {}
            )
        }

        // Live Wallpaper active banner
        composeTestRule.onNodeWithText("Live Wallpaper: Active").assertIsDisplayed()

        // Button should not be present
        composeTestRule.onNodeWithText("Set as Live Wallpaper").assertDoesNotExist()

        // Descriptions about double tap and time in the banner should be removed
        composeTestRule.onNodeWithText("Double-tap to cycle, screen unlock cycling & 10s rotation are fully enabled.")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("Double-tap to cycle, screen unlock rotation, and 10-second timers require Live Wallpaper mode.")
            .assertDoesNotExist()
    }

    @Test
    fun `when live wallpaper is not active, shows not active text and activation button which triggers callback`() {
        var activated = false
        composeTestRule.setContent {
            SettingsScreen(
                settings = defaultSettings,
                isLiveWallpaperActive = false,
                onUpdateSettings = {},
                onActivateLiveWallpaper = { activated = true }
            )
        }

        composeTestRule.onNodeWithText("Live Wallpaper: Not Active").assertIsDisplayed()
        val activateBtn = composeTestRule.onNodeWithText("Set as Live Wallpaper")
        activateBtn.assertIsDisplayed()
        activateBtn.performClick()

        assertThat(activated).isTrue()

        // Descriptions about double tap and time should be removed
        composeTestRule.onNodeWithText("Double-tap to cycle, screen unlock rotation, and 10-second timers require Live Wallpaper mode.")
            .assertDoesNotExist()
    }

    @Test
    fun `centered cover fit card and its description are removed`() {
        composeTestRule.setContent {
            SettingsScreen(
                settings = defaultSettings,
                isLiveWallpaperActive = true,
                onUpdateSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Centered Cover Fit Active").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "All images automatically preserve their native aspect ratio and are scaled symmetrically to cover the full phone screen without letterboxing."
        ).assertDoesNotExist()
    }

    @Test
    fun `clicking automatic wallpaper cycling enters nested screen, and back button returns to main settings`() {
        composeTestRule.setContent {
            SettingsScreen(
                settings = defaultSettings,
                isLiveWallpaperActive = true,
                onUpdateSettings = {}
            )
        }

        // Main screen displays title Settings and Automatic Wallpaper Cycling entry card
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("On • 15 min").assertIsDisplayed()

        // Click to enter nested screen
        composeTestRule.onNodeWithText("Automatic Wallpaper Cycling").performClick()

        // Nested screen is displayed
        composeTestRule.onNodeWithText("Automatic Cycling").assertIsDisplayed()
        composeTestRule.onNodeWithText("Change Interval").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 min").assertIsDisplayed()

        // Click Back
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Main screen is displayed again
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Automatic Cycling").assertDoesNotExist()
    }

    @Test
    fun `in nested screen, when isEnabled is false extra interval options are not displayed`() {
        val disabledSettings = defaultSettings.copy(isEnabled = false)

        composeTestRule.setContent {
            SettingsScreen(
                settings = disabledSettings,
                isLiveWallpaperActive = true,
                onUpdateSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Off").assertIsDisplayed()
        composeTestRule.onNodeWithText("Automatic Wallpaper Cycling").performClick()

        // In nested screen
        composeTestRule.onNodeWithText("Automatic Cycling").assertIsDisplayed()
        // Interval options should NOT be displayed when disabled
        composeTestRule.onNodeWithText("Change Interval").assertDoesNotExist()
    }

    @Test
    fun `in nested screen, selecting interval updates settings`() {
        var updatedSettings: WallpaperSettings? = null

        composeTestRule.setContent {
            SettingsScreen(
                settings = defaultSettings,
                isLiveWallpaperActive = true,
                onUpdateSettings = { updatedSettings = it }
            )
        }

        composeTestRule.onNodeWithText("Automatic Wallpaper Cycling").performClick()
        composeTestRule.onNodeWithText("1 hour").performClick()

        assertThat(updatedSettings).isNotNull()
        assertThat(updatedSettings?.intervalSeconds).isEqualTo(3600L)
        assertThat(updatedSettings?.intervalMinutes).isEqualTo(60L)
    }
}
