package com.pereviader.backgroundswitcher.data.mediastore

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreContentObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var observer: ContentObserver? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var debounceJob: Job? = null

    fun register(onNewMediaDetected: () -> Unit) {
        if (observer != null) return

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // Debounce rapid camera/burst events
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(2000L)
                    onNewMediaDetected()
                }
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    fun unregister() {
        observer?.let {
            context.contentResolver.unregisterContentObserver(it)
            observer = null
        }
    }
}
