package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.SearchCandidate
import com.example.mobile_image_retrieval.domain.model.MediaType
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchMode
import com.example.mobile_image_retrieval.domain.model.ResultSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchEngineTest {
    @Test fun `OCR chronological limit includes matches beyond the first page`() = runTest {
        val candidates = (1L..600L).map { candidate(it, floatArrayOf()) }
        val matches = (1L..600L).toSet()
        val newest = engine(candidates).search(null, SearchFilters(searchMode = SearchMode.OCR, sort = ResultSort.NEWEST_FIRST), 3, textMatches = matches)
        assertEquals(listOf(600L, 599L, 598L), newest.map { it.media.mediaId })
        val oldest = engine(candidates.reversed()).search(null, SearchFilters(searchMode = SearchMode.OCR, sort = ResultSort.OLDEST_FIRST), 3, textMatches = matches)
        assertEquals(listOf(1L, 2L, 3L), oldest.map { it.media.mediaId })
    }

    @Test fun `equal relevance has stable newest first tie breaking independent of page order`() = runTest {
        val candidates = (1L..600L).map { candidate(it, floatArrayOf(1f, 0f)) }
        for (order in listOf(candidates, candidates.reversed())) {
            val results = engine(order).search(floatArrayOf(1f, 0f), SearchFilters(), 3)
            assertEquals(listOf(600L, 599L, 598L), results.map { it.media.mediaId })
        }
    }

    @Test fun `OCR with no text matches skips scanning the photo index`() = runTest {
        val engine = SemanticSearchEngine(SearchCandidateSource { _, _, _ -> error("Should not scan") })
        assertTrue(engine.search(null, SearchFilters(searchMode = SearchMode.OCR)).isEmpty())
    }

    private fun candidate(id: Long, vector: FloatArray) = SearchCandidate(
        id, "content://$id", MediaType.IMAGE, null, id, null, 0, null, null, null, null, null,
        EmbeddingCodec.encode(vector), vector.size,
    )

    private fun engine(candidates: List<SearchCandidate>) = SemanticSearchEngine(SearchCandidateSource { _, limit, offset -> candidates.drop(offset).take(limit) })

    @Test fun `face filtering happens before top k across every page`() = runTest {
        val candidates = (1L..513L).map { candidate(it, if (it == 513L) floatArrayOf(0f, 1f) else floatArrayOf(1f, 0f)) }
        val engine = SemanticSearchEngine(
            SearchCandidateSource { _, limit, offset -> candidates.drop(offset).take(limit) },
            FaceCandidateSource { ids -> if (513L in ids) mapOf(513L to listOf(floatArrayOf(1f, 0f))) else emptyMap() },
        )
        val results = engine.search(floatArrayOf(1f, 0f), SearchFilters(), limit = 1, people = listOf(floatArrayOf(1f, 0f)))
        assertEquals(listOf(513L), results.map { it.media.mediaId })
    }

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
