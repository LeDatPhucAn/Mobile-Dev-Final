package com.example.mobile_image_retrieval.data.mediastore

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.mobile_image_retrieval.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class PhotoCopyRepository(private val resolver: ContentResolver) {
    suspend fun saveCopy(photo: MediaItem): Uri = withContext(Dispatchers.IO) {
        val source = photo.uri.toUri()
        val mime = resolver.getType(source) ?: photo.mimeType
        require(mime?.startsWith("image/") == true) { "This photo's image format is unavailable." }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: photo.displayName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
            ?: "img"
        val base = photo.displayName?.substringBeforeLast('.')?.replace(Regex("[\\p{Cntrl}/\\\\]"), "_")?.take(80)?.ifBlank { null } ?: "Photo"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$base-copy-${UUID.randomUUID().toString().take(8)}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Photo Search")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val coroutine = currentCoroutineContext()
        // Open the original before creating a destination, and stream without decoding/recompressing.
        resolver.openInputStream(source)?.use { input ->
            val destination = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IOException("Could not create a photo copy. Check available storage.")
            try {
                val output = resolver.openOutputStream(destination, "w") ?: throw IOException("Could not write the photo copy.")
                var copied = 0L
                output.use {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        it.write(buffer, 0, count)
                        copied += count
                    }
                }
                if (copied == 0L) throw IOException("The original photo is empty.")
                coroutine.ensureActive()
                val published = resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                if (published != 1) throw IOException("Could not finish saving the photo copy.")
                destination
            } catch (failure: Throwable) {
                // Only remove the new, app-owned row if copying/publishing failed.
                try { resolver.delete(destination, null, null) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                throw failure
            }
        } ?: throw IOException("The original photo is no longer readable.")
    }
}
