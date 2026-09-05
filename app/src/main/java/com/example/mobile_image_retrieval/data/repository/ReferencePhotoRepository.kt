package com.example.mobile_image_retrieval.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import com.example.mobile_image_retrieval.ai.ImageEmbeddingModel
import com.example.mobile_image_retrieval.ai.PersonNames
import com.example.mobile_image_retrieval.ai.FaceAnalyzer
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.PersonDao
import com.example.mobile_image_retrieval.data.db.PersonEntity
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReferencePhotoRepository(
    private val resolver: ContentResolver,
    private val imageModel: ImageEmbeddingModel,
    private val personDao: PersonDao,
    private val faceAnalyzer: FaceAnalyzer,
    private val faceLoader: FacePhotoLoader = FacePhotoLoader(resolver),
) {
    val people = personDao.observePeople()

    suspend fun embed(uri: String): FloatArray {
        val bitmap = loadThumbnail(uri)
        return try { imageModel.embed(bitmap) } finally { bitmap.recycle() }
    }

    suspend fun savePerson(name: String, uri: String, existingId: Long? = null) {
        val handle = PersonNames.handle(name)
        val existing = personDao.byHandles(listOf(handle)).singleOrNull()
        require(existing == null || existing.id == existingId) { "@$handle is already saved. Choose another name." }
        require(existingId == null || existing?.id == existingId) { "This saved person no longer exists." }
        val bitmap = faceLoader.load(uri)
        try {
            val faces = faceAnalyzer.analyze(bitmap)
            require(faces.isNotEmpty()) { "No clear face found. Choose a closer, well-lit photo of this person." }
            require(faces.size == 1) { "More than one face found. Choose or crop a photo showing only this person." }
            val face = faces.single()
            require(face.detection.score >= .7f && face.detection.right - face.detection.left >= 60 && face.detection.bottom - face.detection.top >= 60) {
                "The face is too small or unclear. Choose a closer photo."
            }
            val embedding = face.embedding
            val thumbnail = withContext(Dispatchers.IO) {
                val scale = 256f / maxOf(bitmap.width, bitmap.height)
                val preview = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                try {
                    ByteArrayOutputStream().use { output ->
                        check(preview.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "Could not save the reference photo." }
                        output.toByteArray()
                    }
                } finally { if (preview !== bitmap) preview.recycle() }
            }
            if (existingId != null) {
                check(personDao.updateFace(existingId, thumbnail, EmbeddingCodec.encode(embedding), embedding.size, FaceModelContract.VERSION) == 1) { "This person was removed while saving." }
            } else personDao.insert(PersonEntity(
                name = name.trim().removePrefix("@"), handle = handle, thumbnail = thumbnail,
                embedding = EmbeddingCodec.encode(embedding), embeddingDimension = embedding.size,
                embeddingModel = FaceModelContract.VERSION,
            ))
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun removePerson(id: Long) = personDao.delete(id)

    private suspend fun loadThumbnail(uri: String): Bitmap = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        try {
            resolver.loadThumbnail(parsed, Size(256, 256), null)
        } catch (_: IOException) {
            // Some document providers do not implement thumbnails. Decode at a bounded size.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, parsed)) { decoder, info, _ ->
                val scale = 256f / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
    }
}
