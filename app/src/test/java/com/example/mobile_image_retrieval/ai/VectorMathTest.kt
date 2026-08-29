package com.example.mobile_image_retrieval.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.sqrt

class VectorMathTest {
    @Test fun `normalization produces unit length`() {
        val result = VectorMath.l2NormalizeInPlace(floatArrayOf(3f, 4f))
        assertEquals(0.6f, result[0], 0.0001f)
        assertEquals(0.8f, result[1], 0.0001f)
        assertEquals(1f, sqrt(VectorMath.dot(result, result)), 0.0001f)
    }

    @Test fun `dot of orthogonal normalized vectors is zero`() = assertEquals(
        0f, VectorMath.dot(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0f,
    )

    @Test fun `identical normalized vectors have cosine one`() = assertEquals(
        1f, VectorMath.dot(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f)), 0f,
    )

    @Test fun `zero vector cannot be normalized`() {
        assertThrows(IllegalArgumentException::class.java) { VectorMath.l2NormalizeInPlace(FloatArray(3)) }
    }
}
