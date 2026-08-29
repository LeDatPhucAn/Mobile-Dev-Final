package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.SearchCandidate
import com.example.mobile_image_retrieval.domain.model.MediaType
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchEngineTest {
    private fun candidate(id: Long, vector: FloatArray) = SearchCandidate(
        id, "content://$id", MediaType.IMAGE, null, id, null, 0, null, null, null, null, null,
        EmbeddingCodec.encode(vector), vector.size,
    )

    private fun engine(candidates: List<SearchCandidate>) = SemanticSearchEngine { _, limit, offset -> candidates.drop(offset).take(limit) }

    @Test fun `empty index returns empty results`() = runTest { assertTrue(engine(emptyList()).search(floatArrayOf(1f, 0f), SearchFilters()).isEmpty()) }

    @Test fun `ranking uses normalized dot product`() = runTest {
        val results = engine(listOf(candidate(1, floatArrayOf(0f, 1f)), candidate(2, floatArrayOf(1f, 0f))))
            .search(floatArrayOf(1f, 0f), SearchFilters())
        assertEquals(listOf(2L, 1L), results.map { it.media.mediaId })
    }

    @Test fun `top k is bounded`() = runTest {
        val results = engine(listOf(candidate(1, floatArrayOf(1f, 0f)), candidate(2, floatArrayOf(.8f, .2f)), candidate(3, floatArrayOf(0f, 1f))))
            .search(floatArrayOf(1f, 0f), SearchFilters(), limit = 2)
        assertEquals(listOf(1L, 2L), results.map { it.media.mediaId })
    }

    @Test fun `identical vectors tie at cosine one`() = runTest {
        val results = engine(listOf(candidate(1, floatArrayOf(1f, 0f)), candidate(2, floatArrayOf(1f, 0f))))
            .search(floatArrayOf(1f, 0f), SearchFilters())
        assertEquals(2, results.size)
        results.forEach { assertEquals(1f, it.rawSimilarity, 0f) }
    }
}
