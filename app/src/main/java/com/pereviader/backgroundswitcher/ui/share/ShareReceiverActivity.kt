package com.pereviader.backgroundswitcher.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pereviader.backgroundswitcher.data.remote.GooglePhotosShareParser
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.ui.MainActivity
import com.pereviader.backgroundswitcher.ui.theme.BackgroundSwitcherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

sealed class ImportState {
    data object Loading : ImportState()
    data class Success(val albumName: String, val photoCount: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var albumRepository: AlbumRepository

    @Inject
    lateinit var googlePhotosParser: GooglePhotosShareParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BackgroundSwitcherTheme {
                var state by remember { mutableStateOf<ImportState>(ImportState.Loading) }

                LaunchedEffect(Unit) {
                    processIntent(intent) { newState ->
                        state = newState
                    }
                }

                ShareReceiverDialog(
                    state = state,
                    onOpenApp = {
                        openMainActivity()
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }

    private suspend fun processIntent(intent: Intent, onStateChange: (ImportState) -> Unit) {
        val action = intent.action
        val type = intent.type

        when {
            action == Intent.ACTION_SEND && type?.startsWith("text/") == true -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val url = googlePhotosParser.extractUrl(sharedText)
                if (url != null) {
                    val result = albumRepository.importGooglePhotosAlbum(url)
                    if (result.isSuccess) {
                        val album = result.getOrNull()
                        onStateChange(
                            ImportState.Success(
                                albumName = album?.name ?: "Google Photos Album",
                                photoCount = album?.photoCount ?: 0
                            )
                        )
                        delay(1200)
                        openMainActivity()
                        finish()
                    } else {
                        val error = result.exceptionOrNull()?.localizedMessage
                            ?: "Failed to parse Google Photos album."
                        onStateChange(ImportState.Error(error))
                    }
                } else {
                    onStateChange(
                        ImportState.Error("No valid Google Photos link was found in the shared text.")
                    )
                }
            }

            action == Intent.ACTION_SEND && type?.startsWith("image/") == true -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    val result = albumRepository.importSharedPhotos(listOf(streamUri.toString()))
                    if (result.isSuccess) {
                        onStateChange(ImportState.Success("Shared Photos", 1))
                        delay(1000)
                        openMainActivity()
                        finish()
                    } else {
                        onStateChange(ImportState.Error("Failed to import photo: ${result.exceptionOrNull()?.message}"))
                    }
                } else {
                    onStateChange(ImportState.Error("No image received."))
                }
            }

            action == Intent.ACTION_SEND_MULTIPLE && type?.startsWith("image/") == true -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                if (!uris.isNullOrEmpty()) {
                    val result = albumRepository.importSharedPhotos(uris.map { it.toString() })
                    if (result.isSuccess) {
                        onStateChange(ImportState.Success("Shared Photos", uris.size))
                        delay(1000)
                        openMainActivity()
                        finish()
                    } else {
                        onStateChange(ImportState.Error("Failed to import photos: ${result.exceptionOrNull()?.message}"))
                    }
                } else {
                    onStateChange(ImportState.Error("No images received."))
                }
            }

            else -> {
                onStateChange(ImportState.Error("Unsupported content shared."))
            }
        }
    }

    private fun openMainActivity() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(launchIntent)
    }
}

@Composable
private fun ShareReceiverDialog(
    state: ImportState,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .height(280.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        is ImportState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Importing album from Google Photos...",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Extracting high-resolution wallpapers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is ImportState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Added '${state.albumName}'!",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.photoCount} photos imported and ready to cycle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is ImportState.Error -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Import Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}
