package com.example.mobile_image_retrieval.data.mediastore

import android.content.ContentUris
import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val resolver: ContentResolver) {
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

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
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val taken = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modified = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val width = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val height = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                items += MediaItem(
                    mediaId = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId).toString(),
                    mediaType = MediaType.IMAGE,
                    displayName = cursor.nullableString(name),
                    dateTaken = cursor.nullableLong(taken),
                    dateAdded = cursor.nullableLong(added),
                    dateModified = cursor.getLong(modified),
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

    fun albums(images: List<MediaItem>): List<Album> {
        if (images.isEmpty()) return emptyList()
        val result = mutableListOf(Album("all", "All Photos", images.size, images.first().uri, true))
        val recentCutoffSeconds = (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) / 1000
        val recent = images.filter { (it.dateAdded ?: 0) >= recentCutoffSeconds }
        if (recent.isNotEmpty()) result += Album("recent", "Recently Added", recent.size, recent.maxByOrNull { it.dateAdded ?: 0 }?.uri, true)
        images.groupBy { it.bucketId to it.bucketName }
            .filterKeys { (id, name) -> id != null && !name.isNullOrBlank() }
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (bucket, media) ->
                result += Album(bucket.first!!, bucket.second!!, media.size, media.firstOrNull()?.uri)
            }
        return result.distinctBy { it.id }
    }

    private fun android.database.Cursor.nullableString(index: Int) = if (isNull(index)) null else getString(index)
    private fun android.database.Cursor.nullableLong(index: Int) = if (isNull(index)) null else getLong(index)
    private fun android.database.Cursor.nullableInt(index: Int) = if (isNull(index)) null else getInt(index)
}
