package com.pereviader.backgroundswitcher.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pereviader.backgroundswitcher.domain.usecase.GetWallpaperSettingsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var workScheduler: WorkScheduler

    @Inject
    lateinit var getSettingsUseCase: GetWallpaperSettingsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = getSettingsUseCase().first()
                    if (settings.isEnabled) {
                        workScheduler.scheduleWallpaperCycling(
                            settings.intervalMinutes,
                            settings.onlyOnWifi
                        )
                        workScheduler.schedulePeriodicSync(settings.onlyOnWifi)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
