package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingDao
import com.example.mobile_image_retrieval.data.db.SearchCandidate
import com.example.mobile_image_retrieval.domain.model.ResultSort
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import java.util.PriorityQueue

fun interface SearchCandidateSource {
    suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate>
}

class RoomSearchCandidateSource(private val dao: MediaEmbeddingDao) : SearchCandidateSource {
    override suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate> {
        val bounds = DateRangeCalculator.bounds(filters)
        return dao.searchPage(bounds.startMillis, bounds.endExclusiveMillis, filters.mediaType?.name, limit, offset)
    }
}

class SemanticSearchEngine(private val source: SearchCandidateSource) {
    suspend fun search(queryEmbedding: FloatArray, filters: SearchFilters, limit: Int = 100): List<SearchResult> {
        require(queryEmbedding.isNotEmpty())
        if (limit <= 0) return emptyList()
        val query = queryEmbedding.copyOf()
        VectorMath.l2NormalizeInPlace(query)
        val heap = PriorityQueue<SearchResult>(compareBy { it.rawSimilarity })
        var offset = 0
        val pageSize = 512
        while (true) {
            val page = source.page(filters, pageSize, offset)
            if (page.isEmpty()) break
            for (candidate in page) {
                if (candidate.embeddingDimension != query.size) continue
                val score = EmbeddingCodec.dot(candidate.embedding, query, candidate.embeddingDimension)
                if (heap.size < limit) heap += SearchResult(candidate.toMediaItem(), score)
                else if (score > (heap.peek()?.rawSimilarity ?: Float.NEGATIVE_INFINITY)) {
                    heap.poll()
                    heap += SearchResult(candidate.toMediaItem(), score)
                }
            }
            offset += page.size
            if (page.size < pageSize) break
        }
        val results = heap.toMutableList()
        when (filters.sort) {
            ResultSort.MOST_RELEVANT -> results.sortByDescending { it.rawSimilarity }
            ResultSort.NEWEST_FIRST -> results.sortByDescending { it.media.dateTaken ?: (it.media.dateAdded ?: 0L) * 1000 }
            ResultSort.OLDEST_FIRST -> results.sortBy { it.media.dateTaken ?: (it.media.dateAdded ?: 0L) * 1000 }
        }
        return results
    }
}
