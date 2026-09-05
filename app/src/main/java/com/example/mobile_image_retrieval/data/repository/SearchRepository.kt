package com.example.mobile_image_retrieval.data.repository

import com.example.mobile_image_retrieval.ai.SemanticSearchEngine
import com.example.mobile_image_retrieval.ai.TextEmbeddingModel
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingDao
import com.example.mobile_image_retrieval.data.db.SearchHistoryDao
import com.example.mobile_image_retrieval.data.db.SearchHistoryEntity
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow
import android.os.SystemClock
import com.example.mobile_image_retrieval.ai.PersonalizedQueryParser
import com.example.mobile_image_retrieval.ai.QueryEmbeddingComposer
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.PersonDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchExecution(
    val results: List<SearchResult>,
    val queryEmbeddingMillis: Long,
    val vectorScanMillis: Long,
    val totalMillis: Long,
)

class SearchRepository(
    private val textModel: TextEmbeddingModel,
    private val engine: SemanticSearchEngine,
    private val historyDao: SearchHistoryDao,
    private val mediaDao: MediaEmbeddingDao,
    private val personDao: PersonDao,
    private val referencePhotos: ReferencePhotoRepository,
) {
    val history: Flow<List<SearchHistoryEntity>> = historyDao.observeRecent()

    suspend fun search(query: String, filters: SearchFilters, limit: Int = 100, imageUri: String? = null): SearchExecution {
        val totalStarted = SystemClock.elapsedRealtime()
        val embeddingStarted = SystemClock.elapsedRealtime()
        val parsed = PersonalizedQueryParser.parse(query)
        val people = if (parsed.handles.isEmpty()) emptyList() else personDao.byHandles(parsed.handles)
        val missing = parsed.handles.filter { handle -> people.none { it.handle == handle } }
        require(missing.isEmpty()) { "Unknown ${missing.joinToString { "@$it" }}. Add a reference photo in People first." }
        val outdated = people.filter { it.embeddingModel != FaceModelContract.VERSION }
        require(outdated.isEmpty()) { "Update the face photo for ${outdated.joinToString { "@${it.handle}" }} in People to enable face recognition." }
        val faceReferences = people.map { EmbeddingCodec.decode(it.embedding, it.embeddingDimension) }
        val references = mutableListOf<FloatArray>()
        imageUri?.let { references += referencePhotos.embed(it) }
        val text = parsed.text.takeIf { it.isNotBlank() }?.let { textModel.embed(it) }
        val embedding = if (text != null || references.isNotEmpty()) QueryEmbeddingComposer.compose(text, references) else null
        val embeddingMillis = SystemClock.elapsedRealtime() - embeddingStarted
        val scanStarted = SystemClock.elapsedRealtime()
        val results = withContext(Dispatchers.Default) { engine.search(embedding, filters, limit, faceReferences) }
        val scanMillis = SystemClock.elapsedRealtime() - scanStarted
        return SearchExecution(results, embeddingMillis, scanMillis, SystemClock.elapsedRealtime() - totalStarted)
    }

    suspend fun saveHistory(query: String, topResultUri: String?) = historyDao.insert(
        SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis(), topResultUri = topResultUri),
    )

    suspend fun clearHistory() = historyDao.clear()
    suspend fun mediaById(id: Long) = mediaDao.byId(id)?.toMediaItem()
}
