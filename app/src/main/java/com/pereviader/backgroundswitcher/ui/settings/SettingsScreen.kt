package com.pereviader.backgroundswitcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings

@Composable
fun SettingsScreen(
    settings: WallpaperSettings,
    isLiveWallpaperActive: Boolean = false,
    onUpdateSettings: (WallpaperSettings) -> Unit,
    onActivateLiveWallpaper: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAutoCycleScreen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAutoCycleScreen) {
        showAutoCycleScreen = false
    }

    if (showAutoCycleScreen) {
        AutoCycleSettingsScreen(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onNavigateBack = { showAutoCycleScreen = false },
            modifier = modifier
        )
    } else {
        MainSettingsContent(
            settings = settings,
            isLiveWallpaperActive = isLiveWallpaperActive,
            onUpdateSettings = onUpdateSettings,
            onActivateLiveWallpaper = onActivateLiveWallpaper,
            onOpenAutoCycle = { showAutoCycleScreen = true },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainSettingsContent(
    settings: WallpaperSettings,
    isLiveWallpaperActive: Boolean,
    onUpdateSettings: (WallpaperSettings) -> Unit,
    onActivateLiveWallpaper: () -> Unit,
    onOpenAutoCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        // Live Wallpaper Mode Status Card at top
        if (isLiveWallpaperActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Live Wallpaper: Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Wallpaper: Not Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onActivateLiveWallpaper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as Live Wallpaper")
                    }
                }
            }
        }

        // Automatic Wallpaper Cycling (Enters nested screen)
        Card(
            onClick = onOpenAutoCycle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic Wallpaper Cycling", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (settings.isEnabled) {
                            "On • ${getIntervalLabel(settings.intervalSeconds)}"
                        } else {
                            "Off"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Configure automatic wallpaper cycling",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cycle on Screen Unlock Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cycle on Screen Unlock", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Switch to a new wallpaper whenever you unlock or turn on your device (Live Wallpaper mode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.cycleOnUnlock,
                    onCheckedChange = { onUpdateSettings(settings.copy(cycleOnUnlock = it)) }
                )
            }
        }

        // Double-Tap to Cycle Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Double-Tap to Cycle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Double-tap an empty area on your home screen to switch wallpapers immediately (Live Wallpaper mode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.doubleTapToCycle,
                    onCheckedChange = { onUpdateSettings(settings.copy(doubleTapToCycle = it)) }
                )
            }
        }

        // Target Screen
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Target Screen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Where to apply the wallpaper",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetScreen.entries.forEach { screen ->
                        FilterChip(
                            selected = settings.targetScreen == screen,
                            onClick = { onUpdateSettings(settings.copy(targetScreen = screen)) },
                            label = {
                                Text(
                                    when (screen) {
                                        TargetScreen.HOME -> "Home"
                                        TargetScreen.LOCK -> "Lock"
                                        TargetScreen.BOTH -> "Both"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Rotation Order
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Rotation Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RotationOrder.entries.forEach { order ->
                        FilterChip(
                            selected = settings.rotationOrder == order,
                            onClick = { onUpdateSettings(settings.copy(rotationOrder = order)) },
                            label = {
                                Text(
                                    when (order) {
                                        RotationOrder.RANDOM -> "Shuffle"
                                        RotationOrder.CHRONOLOGICAL -> "Chronological"
                                        RotationOrder.LEAST_RECENTLY_USED -> "Least Recently Used"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Wi-Fi Only constraint
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi Only Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Download new cloud photos only on unmetered Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.onlyOnWifi,
                    onCheckedChange = { onUpdateSettings(settings.copy(onlyOnWifi = it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoCycleSettingsScreen(
    settings: WallpaperSettings,
    onUpdateSettings: (WallpaperSettings) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Automatic Cycling",
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Turn on/off toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic Wallpaper Cycling", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rotate wallpapers automatically based on a timer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.isEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(isEnabled = it)) }
                )
            }
        }

        // Extra options when on
        if (settings.isEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Change Interval", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How often to switch to a new photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val intervals = listOf(
                        10L to "10 sec",
                        900L to "15 min",
                        1800L to "30 min",
                        3600L to "1 hour",
                        21600L to "6 hours",
                        86400L to "24 hours"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        intervals.forEach { (seconds, label) ->
                            FilterChip(
                                selected = settings.intervalSeconds == seconds,
                                onClick = {
                                    onUpdateSettings(
                                        settings.copy(
                                            intervalSeconds = seconds,
                                            intervalMinutes = kotlin.math.max(1L, seconds / 60L)
                                        )
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (settings.intervalSeconds == 10L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚡ 10-second auto-rotation active (requires Live Wallpaper mode)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getIntervalLabel(seconds: Long): String = when (seconds) {
    10L -> "10 sec"
    900L -> "15 min"
    1800L -> "30 min"
    3600L -> "1 hour"
    21600L -> "6 hours"
    86400L -> "24 hours"
    else -> "${seconds / 60} min"
}
