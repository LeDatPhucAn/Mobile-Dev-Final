package com.example.mobile_image_retrieval.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import com.example.mobile_image_retrieval.ai.FaceAnalyzer
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.FaceEmbeddingDao
import com.example.mobile_image_retrieval.data.db.FaceEmbeddingEntity
import com.example.mobile_image_retrieval.data.db.FaceIndexEntity
import com.example.mobile_image_retrieval.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FacePhotoLoader(private val resolver: ContentResolver) {
    // Decode a bounded, EXIF-oriented image, preserving the full frame and small group-photo faces.
    suspend fun load(uri: String): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri.toUri())) { decoder, info, _ ->
            val scale = 1280f / maxOf(info.size.width, info.size.height)
            if (scale < 1) decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}

class FaceIndexRepository(private val loader: FacePhotoLoader, private val analyzer: FaceAnalyzer, private val dao: FaceEmbeddingDao) {
    suspend fun index(item: MediaItem) {
        val bitmap = loader.load(item.uri)
        try {
            val faces = analyzer.analyze(bitmap)
            dao.replace(
                FaceIndexEntity(item.mediaId, item.dateModified, FaceModelContract.VERSION),
                faces.mapIndexed { index, face -> FaceEmbeddingEntity(item.mediaId, index, EmbeddingCodec.encode(face.embedding), face.embedding.size) },
            )
        } finally { bitmap.recycle() }
    }
}
