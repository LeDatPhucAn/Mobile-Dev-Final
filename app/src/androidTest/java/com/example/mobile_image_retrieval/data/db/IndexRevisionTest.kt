package com.example.mobile_image_retrieval.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class IndexRevisionTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PhotoSearchDatabase::class.java).build()
    private fun photo(revision: Long) = MediaItem(1, "content://test/1", MediaType.IMAGE, null, null, null, revision, null, null, null, null, null).withoutEmbedding()
    @After fun close() = database.close()

    @Test fun oldVisualInferenceCannotOverwriteNewerPhotoOrEraseItsText() = runBlocking {
        database.mediaEmbeddingDao().ensureMetadata(photo(2))
        database.photoTextDao().upsert(PhotoTextEntity(1, "new text", "new text", 2, "test"))
        database.mediaEmbeddingDao().upsert(photo(1).copy(embedding = EmbeddingCodec.encode(floatArrayOf(1f)), embeddingDimension = 1))
        assertEquals(2L, database.mediaEmbeddingDao().byId(1)!!.dateModified)
        assertEquals("new text", database.photoTextDao().byMediaId(1)!!.text)
    }

    @Test fun oldTextAndFacesCannotReplaceNewerRevision() = runBlocking {
        database.mediaEmbeddingDao().ensureMetadata(photo(2))
        database.photoTextDao().upsert(PhotoTextEntity(1, "new", "new", 2, "test"))
        database.faceEmbeddingDao().replace(FaceIndexEntity(1, 2, "test"), emptyList())
        database.photoTextDao().upsert(PhotoTextEntity(1, "old", "old", 1, "test"))
        database.faceEmbeddingDao().replace(FaceIndexEntity(1, 1, "test"), emptyList())
        assertEquals("new", database.photoTextDao().byMediaId(1)!!.text)
        assertEquals(2L, database.faceEmbeddingDao().indexStates().single().dateModified)
    }

    @Test fun deletionDuringInferenceDoesNotResurrectTextOrFaces() = runBlocking {
        database.mediaEmbeddingDao().ensureMetadata(photo(1))
        database.mediaEmbeddingDao().deleteIds(listOf(1))
        database.photoTextDao().upsert(PhotoTextEntity(1, "late", "late", 1, "test"))
        database.faceEmbeddingDao().replace(FaceIndexEntity(1, 1, "test"), emptyList())
        assertNull(database.photoTextDao().byMediaId(1))
        assertTrue(database.faceEmbeddingDao().indexStates().isEmpty())
    }
}
