package com.pereviader.backgroundswitcher.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var albumRepository: AlbumRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = extractUrl(sharedText)

            if (url != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val result = albumRepository.importGooglePhotosAlbum(url)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val album = result.getOrNull()
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Added '${album?.name}' to Background Switcher!",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Failed to import album: ${result.exceptionOrNull()?.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        finish()
                    }
                }
            } else {
                Toast.makeText(this, "No valid Google Photos link found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun extractUrl(text: String): String? {
        val words = text.split("\\s+".toRegex())
        return words.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }
}
