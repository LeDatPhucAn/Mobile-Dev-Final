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
import com.example.mobile_image_retrieval.ai.OcrModelContract
import com.example.mobile_image_retrieval.ai.VietnameseText
import com.example.mobile_image_retrieval.data.db.PhotoTextDao
import com.example.mobile_image_retrieval.domain.model.SearchMode

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
    private val photoTextDao: PhotoTextDao? = null,
) {
    val history: Flow<List<SearchHistoryEntity>> = historyDao.observeRecent()

    suspend fun search(query: String, filters: SearchFilters, limit: Int = 100, imageUri: String? = null): SearchExecution {
        val totalStarted = SystemClock.elapsedRealtime()
        val embeddingStarted = SystemClock.elapsedRealtime()
        val parsed = PersonalizedQueryParser.parse(VietnameseText.normalize(query))
        require(filters.searchMode != SearchMode.OCR || imageUri == null) { "Choose Normal query to search using an image." }
        val textExpression = if (filters.searchMode == SearchMode.NORMAL) null else VietnameseText.ftsQuery(parsed.text)
        require(filters.searchMode != SearchMode.OCR || textExpression != null) { "Enter words or numbers to find in photos." }
        require(filters.searchMode != SearchMode.OCR || photoTextDao != null) { "Text search is unavailable." }
        val people = if (parsed.handles.isEmpty()) emptyList() else personDao.byHandles(parsed.handles)
        val missing = parsed.handles.filter { handle -> people.none { it.handle == handle } }
        require(missing.isEmpty()) { "Unknown ${missing.joinToString { "@$it" }}. Add a reference photo in People first." }
        val outdated = people.filter { it.embeddingModel != FaceModelContract.VERSION }
        require(outdated.isEmpty()) { "Update the face photo for ${outdated.joinToString { "@${it.handle}" }} in People to enable face recognition." }
        val faceReferences = people.map { EmbeddingCodec.decode(it.embedding, it.embeddingDimension) }
        val references = mutableListOf<FloatArray>()
        imageUri?.let { references += referencePhotos.embed(it) }
        val text = parsed.text.takeIf { it.isNotBlank() && filters.searchMode != SearchMode.OCR }?.let { textModel.embed(it) }
        val embedding = if (text != null || references.isNotEmpty()) QueryEmbeddingComposer.compose(text, references) else null
        val embeddingMillis = SystemClock.elapsedRealtime() - embeddingStarted
        val scanStarted = SystemClock.elapsedRealtime()
        val matches = if (textExpression != null) photoTextDao?.matchingIds(textExpression, OcrModelContract.VERSION)?.toSet().orEmpty() else emptySet()
        val results = withContext(Dispatchers.Default) { engine.search(embedding, filters, limit, faceReferences, matches) }
        val scanMillis = SystemClock.elapsedRealtime() - scanStarted
        return SearchExecution(results, embeddingMillis, scanMillis, SystemClock.elapsedRealtime() - totalStarted)
    }

    suspend fun saveHistory(query: String, topResultUri: String?, mode: SearchMode = SearchMode.NORMAL) = historyDao.insert(
        SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis(), topResultUri = topResultUri, searchMode = mode.name),
    )

    suspend fun clearHistory() = historyDao.clear()
    suspend fun mediaById(id: Long) = mediaDao.byId(id)?.toMediaItem()
}
