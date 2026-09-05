package com.example.mobile_image_retrieval.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import com.example.mobile_image_retrieval.ai.OcrModelContract
import com.example.mobile_image_retrieval.ai.PhotoTextRecognizer
import com.example.mobile_image_retrieval.ai.VietnameseText
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingDao
import com.example.mobile_image_retrieval.data.db.PhotoTextDao
import com.example.mobile_image_retrieval.data.db.PhotoTextEntity
import com.example.mobile_image_retrieval.data.db.withoutEmbedding
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.mobile_image_retrieval.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoTextRepository(
    private val resolver: ContentResolver,
    private val recognizer: PhotoTextRecognizer,
    private val dao: PhotoTextDao,
    private val mediaDao: MediaEmbeddingDao,
) {
    private val mutex = Mutex()

    suspend fun read(item: MediaItem): PhotoTextEntity = mutex.withLock {
        val cached = dao.byMediaId(item.mediaId)
        if (cached?.dateModified == item.dateModified && cached.modelVersion == OcrModelContract.VERSION) return@withLock cached
        val started = SystemClock.elapsedRealtime()
        val bitmap = load(item.uri)
        val decoded = SystemClock.elapsedRealtime()
        val text = try { recognizer.recognize(bitmap) } finally { bitmap.recycle() }
        val recognized = SystemClock.elapsedRealtime()
        val bounded = text.take(OcrModelContract.MAX_CHARACTERS)
        val document = PhotoTextEntity(item.mediaId, bounded, VietnameseText.searchable(bounded), item.dateModified,
            OcrModelContract.VERSION, text.length > bounded.length)
        mediaDao.ensureMetadata(item.withoutEmbedding())
        if (mediaDao.byId(item.mediaId)?.dateModified == item.dateModified) dao.upsert(document)
        Log.d("PhotoTextIndex", "media=${item.mediaId} decode=${decoded - started}ms recognize=${recognized - decoded}ms total=${SystemClock.elapsedRealtime() - started}ms")
        document
    }

    private suspend fun load(uri: String): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri.toUri())) { decoder, info, _ ->
            // Preserve small receipt/screenshot lettering with a bounded 2048-pixel full frame.
            val scale = 2048f / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
