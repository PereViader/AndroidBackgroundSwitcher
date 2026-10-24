package com.pereviader.backgroundswitcher.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private var currentIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntentState = intent

        setContent {
            BackgroundSwitcherTheme {
                var state by remember { mutableStateOf<ImportState>(ImportState.Loading) }
                val activeIntent = currentIntentState

                LaunchedEffect(activeIntent) {
                    if (activeIntent != null) {
                        state = ImportState.Loading
                        processIntent(activeIntent) { newState ->
                            state = newState
                        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState = intent
    }

    internal suspend fun processIntent(intent: Intent, onStateChange: (ImportState) -> Unit) {
        val action = intent.action
        val type = intent.type

        val isSupportedAction = action == Intent.ACTION_SEND ||
            action == Intent.ACTION_SEND_MULTIPLE ||
            action == Intent.ACTION_VIEW

        if (!isSupportedAction) {
            onStateChange(ImportState.Error("Unsupported content shared."))
            return
        }

        // 1. Always check for an extractable Google Photos / web album URL first,
        // regardless of MIME type (Google Photos may set text/*, */*, or image/* when attaching a link preview).
        val albumUrl = extractSharedAlbumUrl(intent)
        if (albumUrl != null) {
            val result = albumRepository.importGooglePhotosAlbum(albumUrl)
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
                    ?: result.exceptionOrNull()?.message
                    ?: "Failed to parse Google Photos album."
                onStateChange(ImportState.Error(error))
            }
            return
        }

        // 2. If no album URL was found, check for shared image stream URIs (single or multiple)
        val canContainImages = type?.startsWith("image/") == true ||
            type == "*/*" ||
            intent.hasExtra(Intent.EXTRA_STREAM)

        if (canContainImages) {
            val imageUris = extractSharedImageUris(intent)
            if (imageUris.isNotEmpty()) {
                val result = albumRepository.importSharedPhotos(imageUris.map { it.toString() })
                if (result.isSuccess) {
                    onStateChange(ImportState.Success("Shared Photos", imageUris.size))
                    delay(1000)
                    openMainActivity()
                    finish()
                } else {
                    val prefix = if (action == Intent.ACTION_SEND_MULTIPLE) {
                        "Failed to import photos"
                    } else {
                        "Failed to import photo"
                    }
                    onStateChange(ImportState.Error("$prefix: ${result.exceptionOrNull()?.message}"))
                }
                return
            }
        }

        // 3. Report appropriate error when neither URL nor image URIs could be extracted
        val hasTextCandidates = extractSharedTextCandidates(intent).any { it.isNotBlank() }
        when {
            type?.startsWith("text/") == true || hasTextCandidates -> {
                onStateChange(
                    ImportState.Error("No valid Google Photos link was found in the shared text.")
                )
            }
            action == Intent.ACTION_SEND && type?.startsWith("image/") == true -> {
                onStateChange(ImportState.Error("No image received."))
            }
            action == Intent.ACTION_SEND_MULTIPLE && type?.startsWith("image/") == true -> {
                onStateChange(ImportState.Error("No images received."))
            }
            else -> {
                onStateChange(ImportState.Error("Unsupported content shared."))
            }
        }
    }

    internal fun extractSharedAlbumUrl(intent: Intent): String? {
        val candidates = extractSharedTextCandidates(intent)

        // First pass: look specifically for a Google Photos link across all candidates
        for (candidate in candidates) {
            val googlePhotosUrl = googlePhotosParser.extractGooglePhotosUrl(candidate)
            if (googlePhotosUrl != null) {
                return googlePhotosUrl
            }
        }

        // Second pass: if this is a text/link share, allow generic http/https URLs
        val type = intent.type
        val isTextOrLinkShare = type == null ||
            type.startsWith("text/") ||
            type == "*/*" ||
            intent.action == Intent.ACTION_VIEW

        if (isTextOrLinkShare) {
            for (candidate in candidates) {
                val genericUrl = googlePhotosParser.extractUrl(candidate)
                if (genericUrl != null) {
                    return genericUrl
                }
            }
        }

        return null
    }

    internal fun extractSharedTextCandidates(intent: Intent): List<String> {
        val candidates = LinkedHashSet<String>()

        fun addCandidate(value: CharSequence?) {
            val str = value?.toString()?.trim()
            if (!str.isNullOrEmpty()) {
                candidates.add(str)
            }
        }

        // 1. Standard EXTRA_TEXT (as CharSequence to support SpannableString, and as String)
        addCandidate(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        addCandidate(intent.getStringExtra(Intent.EXTRA_TEXT))

        // 2. ArrayList<CharSequence> / ArrayList<String> in EXTRA_TEXT
        intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.forEach { addCandidate(it) }
        intent.getStringArrayListExtra(Intent.EXTRA_TEXT)?.forEach { addCandidate(it) }

        // 3. HTML text, subject, and title extras
        addCandidate(intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT))
        addCandidate(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))
        addCandidate(intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT))
        addCandidate(intent.getCharSequenceExtra(Intent.EXTRA_TITLE))

        // 4. Intent data URI (if web link or Google Photos link)
        intent.dataString?.let { dataStr ->
            if (isWebOrGooglePhotosUriString(dataStr)) {
                addCandidate(dataStr)
            }
        }

        // 5. ClipData items (Android 10+ Sharesheet and Google Photos often attach text/URIs here)
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i) ?: continue
                addCandidate(item.text)
                addCandidate(item.htmlText)
                val itemUriStr = item.uri?.toString()
                if (itemUriStr != null && isWebOrGooglePhotosUriString(itemUriStr)) {
                    addCandidate(itemUriStr)
                }
            }
        }

        // 6. EXTRA_STREAM if it is an http/https Uri
        val singleStreamUri = getSingleStreamUri(intent)
        if (singleStreamUri != null && isWebOrGooglePhotosUriString(singleStreamUri.toString())) {
            addCandidate(singleStreamUri.toString())
        }

        // 7. Fallback: scan remaining extras for any CharSequence or web Uri
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                when (val value = extras.get(key)) {
                    is CharSequence -> addCandidate(value)
                    is Uri -> {
                        val uriStr = value.toString()
                        if (isWebOrGooglePhotosUriString(uriStr)) {
                            addCandidate(uriStr)
                        }
                    }
                }
            }
        }

        return candidates.toList()
    }

    internal fun extractSharedImageUris(intent: Intent): List<Uri> {
        val uris = LinkedHashSet<Uri>()

        fun addIfImageUri(uri: Uri?) {
            if (uri != null && !isWebOrGooglePhotosUriString(uri.toString())) {
                uris.add(uri)
            }
        }

        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            getMultipleStreamUris(intent)?.forEach { addIfImageUri(it) }
            addIfImageUri(getSingleStreamUri(intent))
        } else {
            addIfImageUri(getSingleStreamUri(intent))
            getMultipleStreamUris(intent)?.forEach { addIfImageUri(it) }
        }

        if (uris.isEmpty()) {
            val clipData = intent.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    addIfImageUri(clipData.getItemAt(i)?.uri)
                }
            }
        }

        return uris.toList()
    }

    private fun getSingleStreamUri(intent: Intent): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }.getOrNull()
    }

    private fun getMultipleStreamUris(intent: Intent): List<Uri>? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
        }.getOrNull()
    }

    private fun isWebOrGooglePhotosUriString(uriStr: String): Boolean {
        return uriStr.startsWith("http://", ignoreCase = true) ||
            uriStr.startsWith("https://", ignoreCase = true) ||
            uriStr.contains("photos.app.goo.gl", ignoreCase = true) ||
            uriStr.contains("photos.google.com", ignoreCase = true) ||
            uriStr.contains("goo.gl/photos", ignoreCase = true)
    }

    private fun openMainActivity() {
        if (baseContext == null) return
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
