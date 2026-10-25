package com.pereviader.backgroundswitcher.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.pereviader.backgroundswitcher.core.model.PhotoItem
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CycleWallpaperUseCaseTest {

    private lateinit var context: Context
    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var useCase: CycleWallpaperUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wallpaperRepository = mockk(relaxed = true)
        coEvery { wallpaperRepository.isLiveWallpaperActive() } returns false
        coEvery { wallpaperRepository.applyWallpaper(any(), any()) } returns Result.success(Unit)

        useCase = CycleWallpaperUseCase(
            context = context,
            wallpaperRepository = wallpaperRepository
        )
    }

    @Test
    fun `in Shared Mode, resolves active SHARED candidate and marks photo displayed for SHARED scope`() = runTest {
        val sharedPhoto = PhotoItem(id = "p_shared", albumId = "a1", uri = "content://shared")
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val settings = WallpaperSettings(isSeparateScreensMode = false, targetScreen = TargetScreen.BOTH)

        coEvery { wallpaperRepository.getSettings() } returns flowOf(settings)
        coEvery { wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.SHARED) } returns sharedPhoto
        coEvery { wallpaperRepository.getActiveListTargetScreen(ScheduleScreenScope.SHARED) } returns TargetScreen.BOTH
        coEvery { wallpaperRepository.loadAndCropBitmap(sharedPhoto, any(), any()) } returns bitmap

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { wallpaperRepository.applyWallpaper(bitmap, TargetScreen.BOTH) }
        coVerify(exactly = 1) { wallpaperRepository.markPhotoDisplayed("p_shared", ScheduleScreenScope.SHARED) }
    }

    @Test
    fun `in Separate Screens Mode, resolves HOME and LOCK candidates independently and applies to HOME and LOCK targets`() = runTest {
        val homePhoto = PhotoItem(id = "p_home", albumId = "a1", uri = "content://home")
        val lockPhoto = PhotoItem(id = "p_lock", albumId = "a1", uri = "content://lock")
        val homeBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val lockBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val settings = WallpaperSettings(isSeparateScreensMode = true)

        coEvery { wallpaperRepository.getSettings() } returns flowOf(settings)
        coEvery { wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.HOME) } returns homePhoto
        coEvery { wallpaperRepository.getNextCandidatePhoto(ScheduleScreenScope.LOCK) } returns lockPhoto
        coEvery { wallpaperRepository.loadAndCropBitmap(homePhoto, any(), any()) } returns homeBitmap
        coEvery { wallpaperRepository.loadAndCropBitmap(lockPhoto, any(), any()) } returns lockBitmap

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { wallpaperRepository.applyWallpaper(homeBitmap, TargetScreen.HOME) }
        coVerify(exactly = 1) { wallpaperRepository.markPhotoDisplayed("p_home", ScheduleScreenScope.HOME) }
        coVerify(exactly = 1) { wallpaperRepository.applyWallpaper(lockBitmap, TargetScreen.LOCK) }
        coVerify(exactly = 1) { wallpaperRepository.markPhotoDisplayed("p_lock", ScheduleScreenScope.LOCK) }
    }
}
