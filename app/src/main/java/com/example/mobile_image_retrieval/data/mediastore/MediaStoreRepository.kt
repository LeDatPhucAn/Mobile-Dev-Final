package com.example.mobile_image_retrieval.data.mediastore

import android.content.ContentUris
import android.content.ContentResolver
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.AlbumCatalog
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.IOException

class MediaStoreRepository(private val resolver: ContentResolver) {
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Observe all volumes, including changes made while the album screen is open. */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    suspend fun queryImages(): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val items = ArrayList<MediaItem>()
        val cursor = resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.IS_PENDING} = 0",
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
        ) ?: throw IOException("The photo library could not be read. Please try refreshing it.")
        cursor.use {
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mime = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val taken = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val added = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val modified = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            val width = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val height = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val bucketId = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketName = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                items += MediaItem(
                    mediaId = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId).toString(),
                    mediaType = MediaType.IMAGE,
                    displayName = cursor.nullableString(name),
                    dateTaken = cursor.nullableLong(taken),
                    dateAdded = cursor.nullableLong(added),
                    dateModified = cursor.nullableLong(modified) ?: 0L,
                    width = cursor.nullableInt(width),
                    height = cursor.nullableInt(height),
                    mimeType = cursor.nullableString(mime),
                    bucketId = cursor.nullableString(bucketId),
                    bucketName = cursor.nullableString(bucketName),
                )
            }
        }
        items
    }

    suspend fun loadEmbeddingThumbnail(item: MediaItem, cancellationSignal: CancellationSignal? = null): Bitmap =
        withContext(Dispatchers.IO) {
            resolver.loadThumbnail(item.uri.toUri(), Size(256, 256), cancellationSignal)
        }

    suspend fun albums(): List<Album> {
        val images = queryImages()
        return albums(images)
    }

    fun albums(
        images: List<MediaItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Album> = AlbumCatalog.build(images, nowMillis)

    private fun android.database.Cursor.nullableString(index: Int) = if (index < 0 || isNull(index)) null else getString(index)
    private fun android.database.Cursor.nullableLong(index: Int) = if (index < 0 || isNull(index)) null else getLong(index)
    private fun android.database.Cursor.nullableInt(index: Int) = if (index < 0 || isNull(index)) null else getInt(index)
}
