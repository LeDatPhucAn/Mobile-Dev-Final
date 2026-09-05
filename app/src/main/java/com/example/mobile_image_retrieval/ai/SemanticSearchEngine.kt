package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingDao
import com.example.mobile_image_retrieval.data.db.SearchCandidate
import com.example.mobile_image_retrieval.data.db.FaceEmbeddingDao
import com.example.mobile_image_retrieval.domain.model.ResultSort
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import com.example.mobile_image_retrieval.domain.model.SearchMode
import java.util.PriorityQueue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface SearchCandidateSource {
    suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate>
}

class RoomSearchCandidateSource(private val dao: MediaEmbeddingDao) : SearchCandidateSource {
    override suspend fun page(filters: SearchFilters, limit: Int, offset: Int): List<SearchCandidate> {
        val bounds = DateRangeCalculator.bounds(filters)
        return dao.searchPage(bounds.startMillis, bounds.endExclusiveMillis, filters.mediaType?.name, limit, offset, filters.searchMode == SearchMode.OCR)
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
    suspend fun search(queryEmbedding: FloatArray?, filters: SearchFilters, limit: Int = 100, people: List<FloatArray> = emptyList(), textMatches: Set<Long> = emptySet()): List<SearchResult> {
        require(queryEmbedding?.isNotEmpty() == true || people.isNotEmpty() || filters.searchMode == SearchMode.OCR)
        require(people.isEmpty() || faceSource != null) { "Face search is unavailable." }
        if (limit <= 0) return emptyList()
        if (filters.searchMode == SearchMode.OCR && textMatches.isEmpty()) return emptyList()
        val query = queryEmbedding?.copyOf()?.let(VectorMath::l2NormalizeInPlace)
        val normalizedPeople = people.map { VectorMath.l2NormalizeInPlace(it.copyOf()) }
        val relevance = compareBy<SearchResult> { it.textMatch }.thenBy { it.rawSimilarity }
            .thenBy { it.media.dateTaken ?: (it.media.dateAdded ?: 0L) * 1000 }
            .thenBy { it.media.mediaId }
        val newest = compareBy<SearchResult> { it.media.dateTaken ?: (it.media.dateAdded ?: 0L) * 1000 }
            .thenBy { it.media.mediaId }
        // OCR is an exact set of matches: apply chronological ordering before limiting it.
        val ranking = if (filters.searchMode == SearchMode.OCR) when (filters.sort) {
            ResultSort.NEWEST_FIRST -> newest
            ResultSort.OLDEST_FIRST -> newest.reversed()
            ResultSort.MOST_RELEVANT -> relevance
        } else relevance
        val heap = PriorityQueue<SearchResult>(ranking)
        var offset = 0
        val pageSize = 512
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = source.page(filters, pageSize, offset)
            if (page.isEmpty()) break
            val faces = if (people.isEmpty()) emptyMap() else faceSource!!.forMedia(page.map { it.mediaId })
            for (candidate in page) {
                val textMatch = candidate.mediaId in textMatches && filters.searchMode != SearchMode.NORMAL
                if (filters.searchMode == SearchMode.OCR && !textMatch) continue
                val faceScore = if (people.isNotEmpty()) {
                    FaceMatcher.matchAll(normalizedPeople, faces[candidate.mediaId].orEmpty()) ?: continue
                } else null
                if (query != null && candidate.embeddingDimension != query.size) continue
                val score = if (query != null) EmbeddingCodec.dot(candidate.embedding, query, candidate.embeddingDimension) else faceScore ?: 0f
                val result = SearchResult(candidate.toMediaItem(), score, textMatch)
                if (heap.size < limit) heap += result
                else if (ranking.compare(result, heap.peek()) > 0) {
                    heap.poll()
                    heap += result
                }
            }
            offset += page.size
            if (page.size < pageSize) break
        }
        val results = heap.toMutableList()
        when (filters.sort) {
            ResultSort.MOST_RELEVANT -> results.sortWith(ranking.reversed())
            ResultSort.NEWEST_FIRST -> results.sortWith(newest.reversed())
            ResultSort.OLDEST_FIRST -> results.sortWith(newest)
        }
        return results
    }
}
