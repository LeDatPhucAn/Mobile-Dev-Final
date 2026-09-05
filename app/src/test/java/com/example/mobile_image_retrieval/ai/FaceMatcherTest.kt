package com.example.mobile_image_retrieval.ai

import org.junit.Assert.*
import org.junit.Test

class FaceMatcherTest {
    private val alex = floatArrayOf(1f, 0f)
    private val mai = floatArrayOf(0f, 1f)

    @Test fun `all mentioned people require separate faces`() {
        assertEquals(1f, FaceMatcher.matchAll(listOf(alex, mai), listOf(mai, alex))!!, .00001f)
        assertNull(FaceMatcher.matchAll(listOf(alex, mai), listOf(alex)))
        assertNull(FaceMatcher.matchAll(listOf(alex, mai), listOf(alex, alex)))
    }

    @Test fun `an ambiguous face cannot satisfy both people`() {
        val ambiguous = VectorMath.l2NormalizeInPlace(floatArrayOf(1f, 1f))
        assertNull(FaceMatcher.matchAll(listOf(alex, mai), listOf(ambiguous, floatArrayOf(-1f, 0f))))
    }

    @Test fun `assignment recovers when first choices collide`() {
        val ambiguous = VectorMath.l2NormalizeInPlace(floatArrayOf(1f, 1f))
        assertNotNull(FaceMatcher.matchAll(listOf(alex, mai), listOf(ambiguous, alex)))
    }

    @Test fun `unrelated and missing faces do not produce results`() {
        assertNull(FaceMatcher.matchAll(listOf(alex), emptyList()))
        assertNull(FaceMatcher.matchAll(listOf(alex), listOf(mai)))
        assertNull(FaceMatcher.matchAll(listOf(alex), listOf(floatArrayOf(Float.NaN, 0f))))
    }

    @Test fun `alignment removes translation scale and rotation`() {
        val transformed = FaceGeometry.template.map { FacePoint(-it.y * 2 + 300, it.x * 2 + 20) }
        val (a, b, tx, ty) = FaceGeometry.alignment(transformed)
        transformed.zip(FaceGeometry.template).forEach { (source, expected) ->
            assertEquals(expected.x, a * source.x - b * source.y + tx, .001f)
            assertEquals(expected.y, b * source.x + a * source.y + ty, .001f)
        }
    }

    @Test fun `overlapping detections reduce to one face`() {
        val face = DetectedFace(0f, 0f, 100f, 100f, .9f, FaceGeometry.template)
        val duplicate = face.copy(left = 1f, score = .8f)
        val other = face.copy(left = 200f, right = 300f)
        assertEquals(listOf(face, other), FaceGeometry.suppressOverlaps(listOf(duplicate, face, other)))
    }
}
