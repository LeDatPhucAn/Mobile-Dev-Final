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

data class SearchExecution(
    val results: List<SearchResult>,
    val textEmbeddingMillis: Long,
    val vectorScanMillis: Long,
    val totalMillis: Long,
)

class SearchRepository(
    private val textModel: TextEmbeddingModel,
    private val engine: SemanticSearchEngine,
    private val historyDao: SearchHistoryDao,
    private val mediaDao: MediaEmbeddingDao,
) {
    val history: Flow<List<SearchHistoryEntity>> = historyDao.observeRecent()

    suspend fun search(query: String, filters: SearchFilters, limit: Int = 100): SearchExecution {
        val totalStarted = SystemClock.elapsedRealtime()
        val embeddingStarted = SystemClock.elapsedRealtime()
        val embedding = textModel.embed(query)
        val embeddingMillis = SystemClock.elapsedRealtime() - embeddingStarted
        val scanStarted = SystemClock.elapsedRealtime()
        val results = engine.search(embedding, filters, limit)
        val scanMillis = SystemClock.elapsedRealtime() - scanStarted
        return SearchExecution(results, embeddingMillis, scanMillis, SystemClock.elapsedRealtime() - totalStarted)
    }

    suspend fun saveHistory(query: String, topResultUri: String?) = historyDao.insert(
        SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis(), topResultUri = topResultUri),
    )

    suspend fun clearHistory() = historyDao.clear()
    suspend fun mediaById(id: Long) = mediaDao.byId(id)?.toMediaItem()
}
