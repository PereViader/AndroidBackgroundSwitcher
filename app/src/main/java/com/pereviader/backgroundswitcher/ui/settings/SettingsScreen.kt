package com.pereviader.backgroundswitcher.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.core.model.RotationOrder
import com.pereviader.backgroundswitcher.core.model.TargetScreen
import com.pereviader.backgroundswitcher.core.model.WallpaperSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: WallpaperSettings,
    onUpdateSettings: (WallpaperSettings) -> Unit,
    onTriggerCycleNow: () -> Unit = {},
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

        // Manual Wallpaper Rotation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Manual Wallpaper Rotation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Switch wallpaper immediately to the next enabled photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onTriggerCycleNow,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cycle Now")
                }
            }
        }

        // Enable / Disable Master Toggle
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
                        "Rotate wallpapers automatically in the background",
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

        // Interval Setting
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
                    15L to "15 min",
                    30L to "30 min",
                    60L to "1 hour",
                    360L to "6 hours",
                    1440L to "24 hours"
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { (minutes, label) ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { onUpdateSettings(settings.copy(intervalMinutes = minutes)) },
                            label = { Text(label) }
                        )
                    }
                }
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

        // "Cover" Aspect Ratio Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Centered Cover Fit Active",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All images automatically preserve their native aspect ratio and are scaled symmetrically to cover the full phone screen without letterboxing.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
