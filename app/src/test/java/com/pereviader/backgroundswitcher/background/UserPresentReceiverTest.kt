package com.pereviader.backgroundswitcher.background

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserPresentReceiverTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase
    private lateinit var getSettingsUseCase: GetWallpaperSettingsUseCase
    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var receiver: UserPresentReceiver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        cycleWallpaperUseCase = mockk(relaxed = true)
        getSettingsUseCase = mockk(relaxed = true)
        wallpaperRepository = mockk(relaxed = true)

        UserPresentReceiver.lastUnlockTime = 0L
        receiver = UserPresentReceiver().apply {
            this.cycleWallpaperUseCase = this@UserPresentReceiverTest.cycleWallpaperUseCase
            this.getSettingsUseCase = this@UserPresentReceiverTest.getSettingsUseCase
            this.wallpaperRepository = this@UserPresentReceiverTest.wallpaperRepository
            this.coroutineDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when static wallpaper is active and unlock cycling is enabled, triggers cycleWallpaperUseCase`() = runTest(testDispatcher) {
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns false
        coEvery { getSettingsUseCase() } returns flowOf(WallpaperSettings(isEnabled = true, cycleOnUnlock = true))
        coEvery { cycleWallpaperUseCase() } returns Result.success(Unit)

        receiver.processUserPresent(Intent(Intent.ACTION_USER_PRESENT))
        advanceUntilIdle()

        coVerify(exactly = 1) { cycleWallpaperUseCase() }
    }

    @Test
    fun `when static wallpaper is active and cycleOnUnlock is true even if isEnabled is false, triggers cycleWallpaperUseCase`() = runTest(testDispatcher) {
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns false
        coEvery { getSettingsUseCase() } returns flowOf(WallpaperSettings(isEnabled = false, cycleOnUnlock = true))
        coEvery { cycleWallpaperUseCase() } returns Result.success(Unit)

        receiver.processUserPresent(Intent(Intent.ACTION_USER_PRESENT))
        advanceUntilIdle()

        coVerify(exactly = 1) { cycleWallpaperUseCase() }
    }

    @Test
    fun `when live wallpaper is active, does not trigger cycleWallpaperUseCase`() = runTest(testDispatcher) {
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns true
        coEvery { getSettingsUseCase() } returns flowOf(WallpaperSettings(isEnabled = true, cycleOnUnlock = true))

        receiver.processUserPresent(Intent(Intent.ACTION_USER_PRESENT))
        advanceUntilIdle()

        coVerify(exactly = 0) { cycleWallpaperUseCase() }
    }

    @Test
    fun `when unlock cycling is disabled, does not trigger cycleWallpaperUseCase`() = runTest(testDispatcher) {
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns false
        coEvery { getSettingsUseCase() } returns flowOf(WallpaperSettings(isEnabled = true, cycleOnUnlock = false))

        receiver.processUserPresent(Intent(Intent.ACTION_USER_PRESENT))
        advanceUntilIdle()

        coVerify(exactly = 0) { cycleWallpaperUseCase() }
    }

    @Test
    fun `when action is not USER_PRESENT, does nothing`() = runTest(testDispatcher) {
        receiver.processUserPresent(Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceUntilIdle()

        coVerify(exactly = 0) { cycleWallpaperUseCase() }
    }
}
