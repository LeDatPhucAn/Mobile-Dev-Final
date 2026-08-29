package com.example.mobile_image_retrieval.data.db

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingCodecTest {
    @Test fun `float32 round trip is exact`() {
        val original = floatArrayOf(-1.25f, 0f, 3.14159f, Float.MIN_VALUE)
        assertArrayEquals(original, EmbeddingCodec.decode(EmbeddingCodec.encode(original), original.size), 0f)
    }

    @Test fun `encoded dot product does not require decoded vector`() {
        val encoded = EmbeddingCodec.encode(floatArrayOf(0.5f, -1f, 2f))
        assertEquals(2.5f, EmbeddingCodec.dot(encoded, floatArrayOf(1f, 0.5f, 1.25f)), 0f)
    }
}
