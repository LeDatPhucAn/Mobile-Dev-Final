package com.example.mobile_image_retrieval.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mobile_image_retrieval.ai.ImageEmbeddingModel
import com.example.mobile_image_retrieval.ai.RoomSearchCandidateSource
import com.example.mobile_image_retrieval.ai.SemanticSearchEngine
import com.example.mobile_image_retrieval.ai.TextEmbeddingModel
import com.example.mobile_image_retrieval.ai.VectorMath
import com.example.mobile_image_retrieval.ai.FaceAnalyzer
import com.example.mobile_image_retrieval.ai.FaceGeometry
import com.example.mobile_image_retrieval.ai.DetectedFace
import com.example.mobile_image_retrieval.ai.RecognizedFace
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.ai.RoomFaceCandidateSource
import com.example.mobile_image_retrieval.data.db.FaceEmbeddingEntity
import com.example.mobile_image_retrieval.data.db.FaceIndexEntity
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingEntity
import com.example.mobile_image_retrieval.data.db.PersonEntity
import com.example.mobile_image_retrieval.data.db.PhotoSearchDatabase
import com.example.mobile_image_retrieval.domain.model.MediaType
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalizedSearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, PhotoSearchDatabase::class.java).build()
    private val embeddedText = mutableListOf<String>()
    private val references = ReferencePhotoRepository(context.contentResolver, object : ImageEmbeddingModel {
        override suspend fun embed(bitmap: Bitmap) = floatArrayOf(1f, 0f)
    }, database.personDao(), object : FaceAnalyzer {
        override suspend fun analyze(bitmap: Bitmap) = List(detectionCount) {
            RecognizedFace(DetectedFace(0f, 0f, 100f, 100f, .99f, FaceGeometry.template), floatArrayOf(1f, 0f))
        }
    })
    private var detectionCount = 1
    private val repository = SearchRepository(
        object : TextEmbeddingModel {
            override suspend fun embed(text: String): FloatArray {
                embeddedText += text
                return floatArrayOf(0f, 1f)
            }
        },
        SemanticSearchEngine(RoomSearchCandidateSource(database.mediaEmbeddingDao()), RoomFaceCandidateSource(database.faceEmbeddingDao())),
        database.searchHistoryDao(), database.mediaEmbeddingDao(), database.personDao(), references,
    )

    @Before fun setup() = runBlocking {
        listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(1f, 1f)).forEachIndexed { index, vector ->
            database.mediaEmbeddingDao().upsert(MediaEmbeddingEntity(
                mediaId = index.toLong(), uri = "content://test/$index", mediaType = MediaType.IMAGE,
                displayName = null, dateTaken = null, dateAdded = null, dateModified = 0,
                width = null, height = null, mimeType = null, bucketId = null, bucketName = null,
                embedding = EmbeddingCodec.encode(VectorMath.l2NormalizeInPlace(vector)), embeddingDimension = 2, indexedAt = 0,
            ))
            val faces = when (index) {
                0 -> listOf(floatArrayOf(1f, 0f))
                1 -> listOf(floatArrayOf(0f, 1f))
                else -> listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
            }
            database.faceEmbeddingDao().replace(FaceIndexEntity(index.toLong(), 0, FaceModelContract.VERSION),
                faces.mapIndexed { faceIndex, embedding -> FaceEmbeddingEntity(index.toLong(), faceIndex, EmbeddingCodec.encode(embedding), 2) })
        }
        database.personDao().insert(PersonEntity(
            name = "Alex", handle = "alex", thumbnail = byteArrayOf(1),
            embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f)), embeddingDimension = 2,
            embeddingModel = FaceModelContract.VERSION,
        ))
        database.personDao().insert(PersonEntity(name = "Mai", handle = "mai", thumbnail = byteArrayOf(1),
            embedding = EmbeddingCodec.encode(floatArrayOf(0f, 1f)), embeddingDimension = 2, embeddingModel = FaceModelContract.VERSION))
    }

    @After fun close() = database.close()

    @Test fun mentionOnlyUsesSavedImageAndContextChangesRanking() = runBlocking {
        assertEquals(setOf(0L, 2L), repository.search("@ALEX", SearchFilters()).results.map { it.media.mediaId }.toSet())
        assertTrue(embeddedText.isEmpty())
        assertEquals(2L, repository.search("@alex beach", SearchFilters()).results.first().media.mediaId)
        assertEquals(listOf("beach"), embeddedText)
    }

    @Test fun multipleMentionsRequireBothIdentitiesBeforeRanking() = runBlocking {
        assertEquals(listOf(2L), repository.search("@alex @mai beach", SearchFilters()).results.map { it.media.mediaId })
        assertEquals(listOf("beach"), embeddedText)
    }

    @Test fun emptyScansPersistAndReplacingPhotosInvalidatesOldFaces() = runBlocking {
        val dao = database.faceEmbeddingDao()
        dao.replace(FaceIndexEntity(0, 0, FaceModelContract.VERSION), emptyList())
        assertTrue(dao.forMedia(listOf(0), FaceModelContract.VERSION).isEmpty())
        assertTrue(dao.indexStates().any { it.mediaId == 0L })
        database.mediaEmbeddingDao().upsert(database.mediaEmbeddingDao().byId(2)!!.copy(dateModified = 1))
        assertTrue(dao.forMedia(listOf(2), FaceModelContract.VERSION).isEmpty())
        assertTrue(dao.indexStates().none { it.mediaId == 2L })
        assertTrue(repository.search("@alex", SearchFilters()).results.isEmpty())
    }

    @Test fun legacyWholePhotoReferencesRequireReEnrollment() = runBlocking {
        database.personDao().insert(PersonEntity(name = "Legacy", handle = "legacy", thumbnail = byteArrayOf(1),
            embedding = EmbeddingCodec.encode(floatArrayOf(1f, 0f)), embeddingDimension = 2))
        val error = runCatching { repository.search("@legacy", SearchFilters()) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Update the face photo"))
        assertTrue(embeddedText.isEmpty())
    }

    @Test fun missingAndRemovedPeopleFailBeforeInference() = runBlocking {
        val missing = runCatching { repository.search("@unknown beach", SearchFilters()) }.exceptionOrNull()
        assertTrue(missing is IllegalArgumentException)
        assertTrue(missing?.message.orEmpty().contains("@unknown"))
        database.personDao().delete(database.personDao().byHandles(listOf("alex")).single().id)
        assertTrue(runCatching { repository.search("@alex", SearchFilters()) }.isFailure)
        assertTrue(embeddedText.isEmpty())
    }

    @Test fun imageOnlySearchAndSavedReferenceWorkWithoutOriginalPhoto() = runBlocking {
        val file = File.createTempFile("reference-test", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = Uri.fromFile(file).toString()
            assertEquals(0L, repository.search("", SearchFilters(), imageUri = uri).results.first().media.mediaId)
            references.savePerson("Mai Anh", uri)
            val saved = database.personDao().byHandles(listOf("mai_anh")).single()
            assertEquals(FaceModelContract.VERSION, saved.embeddingModel)
            detectionCount = 0
            assertTrue(runCatching { references.savePerson("No Face", uri) }.isFailure)
            detectionCount = 2
            assertTrue(runCatching { references.savePerson("Group", uri) }.isFailure)
            detectionCount = 1
            references.savePerson("Mai Anh", uri, saved.id)
            file.delete()
            assertEquals(0L, repository.search("@mai_anh", SearchFilters()).results.first().media.mediaId)
            assertTrue(embeddedText.isEmpty())
        } finally {
            file.delete()
        }
    }
}
