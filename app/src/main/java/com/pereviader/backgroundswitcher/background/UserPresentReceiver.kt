package com.pereviader.backgroundswitcher.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import com.pereviader.backgroundswitcher.domain.usecase.CycleWallpaperUseCase
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserPresentReceiver : BroadcastReceiver() {

    @Inject
    lateinit var cycleWallpaperUseCase: CycleWallpaperUseCase

    @Inject
    lateinit var getSettingsUseCase: GetWallpaperSettingsUseCase

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

    companion object {
        private var lastUnlockTime = 0L
        private const val UNLOCK_DEBOUNCE_MILLIS = 2000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastUnlockTime < UNLOCK_DEBOUNCE_MILLIS) return
            lastUnlockTime = now

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (wallpaperRepository.isLiveWallpaperActive()) {
                        // LiveWallpaperService registers its own dynamic receiver for ACTION_USER_PRESENT
                        return@launch
                    }
                    val settings = getSettingsUseCase().first()
                    if (settings.isEnabled && settings.cycleOnUnlock) {
                        cycleWallpaperUseCase()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
