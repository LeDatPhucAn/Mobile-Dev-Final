package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingDao
import com.example.mobile_image_retrieval.data.db.SearchCandidate
import com.example.mobile_image_retrieval.data.db.FaceEmbeddingDao
import com.example.mobile_image_retrieval.domain.model.ResultSort
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import java.util.PriorityQueue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface SearchCandidateSource {
    suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate>
}

class RoomSearchCandidateSource(private val dao: MediaEmbeddingDao) : SearchCandidateSource {
    override suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate> {
        val bounds = DateRangeCalculator.bounds(filters)
        return dao.searchPage(bounds.startMillis, bounds.endExclusiveMillis, filters.mediaType?.name, limit, offset)
    }
}

fun interface FaceCandidateSource {
    suspend fun forMedia(ids: List<Long>): Map<Long, List<FloatArray>>
}

class RoomFaceCandidateSource(private val dao: FaceEmbeddingDao) : FaceCandidateSource {
    override suspend fun forMedia(ids: List<Long>): Map<Long, List<FloatArray>> =
        dao.forMedia(ids, FaceModelContract.VERSION).groupBy { it.mediaId }.mapValues { (_, faces) ->
            faces.map { EmbeddingCodec.decode(it.embedding, it.embeddingDimension) }
        }
}

class SemanticSearchEngine(private val source: SearchCandidateSource, private val faceSource: FaceCandidateSource? = null) {
    suspend fun search(queryEmbedding: FloatArray?, filters: SearchFilters, limit: Int = 100, people: List<FloatArray> = emptyList()): List<SearchResult> {
        require(queryEmbedding?.isNotEmpty() == true || people.isNotEmpty())
        require(people.isEmpty() || faceSource != null) { "Face search is unavailable." }
        if (limit <= 0) return emptyList()
        val query = queryEmbedding?.copyOf()?.let(VectorMath::l2NormalizeInPlace)
        val normalizedPeople = people.map { VectorMath.l2NormalizeInPlace(it.copyOf()) }
        val heap = PriorityQueue<SearchResult>(compareBy { it.rawSimilarity })
        var offset = 0
        val pageSize = 512
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = source.page(filters, pageSize, offset)
            if (page.isEmpty()) break
            val faces = if (people.isEmpty()) emptyMap() else faceSource!!.forMedia(page.map { it.mediaId })
            for (candidate in page) {
                val faceScore = if (people.isNotEmpty()) {
                    FaceMatcher.matchAll(normalizedPeople, faces[candidate.mediaId].orEmpty()) ?: continue
                } else null
                if (query != null && candidate.embeddingDimension != query.size) continue
                val score = if (query != null) EmbeddingCodec.dot(candidate.embedding, query, candidate.embeddingDimension) else faceScore!!
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
