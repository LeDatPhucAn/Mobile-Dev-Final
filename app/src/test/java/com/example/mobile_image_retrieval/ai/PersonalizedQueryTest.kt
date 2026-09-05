package com.example.mobile_image_retrieval.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedQueryTest {
    @Test fun `mentions resolve case insensitively and deduplicate without losing context`() {
        val query = PersonalizedQueryParser.parse("@Alex and @mai_anh at the beach with @ALEX")
        assertEquals(listOf("alex", "mai_anh"), query.handles)
        assertEquals("and at the beach with", query.text)
    }

    @Test fun `mention only query does not need a text embedding`() {
        assertEquals(PersonalizedQuery("", listOf("alex")), PersonalizedQueryParser.parse("@Alex!"))
    }

    @Test fun `email addresses are preserved as text`() {
        val query = PersonalizedQueryParser.parse("receipt from alex@example.com")
        assertTrue(query.handles.isEmpty())
        assertEquals("receipt from alex@example.com", query.text)
    }

    @Test fun `names support unicode spaces and canonical equivalents`() {
        assertEquals("mai_anh", PersonNames.handle(" Mai Anh "))
        assertEquals("trần", PersonNames.handle("Trần"))
        assertEquals(PersonNames.handle("José"), PersonNames.handle("Jose\u0301"))
        assertEquals(listOf("josé"), PersonalizedQueryParser.parse("@Jose\u0301").handles)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `punctuation cannot create an unusable handle`() { PersonNames.handle("Alex Smith!") }

    @Test(expected = IllegalArgumentException::class)
    fun `empty mention is rejected`() { PersonalizedQueryParser.parse("beach with @") }

    @Test fun `image only search uses the image direction and does not mutate it`() {
        val image = floatArrayOf(3f, 4f)
        assertArrayEquals(floatArrayOf(.6f, .8f), QueryEmbeddingComposer.compose(null, listOf(image)), .00001f)
        assertArrayEquals(floatArrayOf(3f, 4f), image, 0f)
    }

    @Test fun `text and people both influence ranking direction`() {
        val result = QueryEmbeddingComposer.compose(floatArrayOf(1f, 0f), listOf(floatArrayOf(0f, 1f)))
        assertEquals(result[0], result[1], .00001f)
        assertEquals(1f, VectorMath.dot(result, result), .00001f)
    }

    @Test fun `multiple references do not drown out text context`() {
        val result = QueryEmbeddingComposer.compose(
            floatArrayOf(1f, 0f), listOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)),
        )
        assertEquals(result[0], result[1], .00001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `incompatible saved embeddings are rejected`() {
        QueryEmbeddingComposer.compose(floatArrayOf(1f, 0f), listOf(floatArrayOf(1f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty search is rejected`() { QueryEmbeddingComposer.compose(null, emptyList()) }
}
