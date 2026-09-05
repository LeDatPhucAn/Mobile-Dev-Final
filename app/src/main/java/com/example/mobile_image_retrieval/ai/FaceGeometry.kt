package com.example.mobile_image_retrieval.ai

import kotlin.math.max
import kotlin.math.min

data class FacePoint(val x: Float, val y: Float)
data class DetectedFace(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val score: Float, val landmarks: List<FacePoint>,
)

object FaceGeometry {
    val template = listOf(
        FacePoint(38.2946f, 51.6963f), FacePoint(73.5318f, 51.5014f),
        FacePoint(56.0252f, 71.7366f), FacePoint(41.5493f, 92.3655f), FacePoint(70.7299f, 92.2041f),
    )

    /** Least-squares similarity transform: u = a*x - b*y + tx, v = b*x + a*y + ty. */
    fun alignment(points: List<FacePoint>): FloatArray {
        require(points.size == 5 && points.all { it.x.isFinite() && it.y.isFinite() })
        val sx = points.map { it.x.toDouble() }.average()
        val sy = points.map { it.y.toDouble() }.average()
        val dx = template.map { it.x.toDouble() }.average()
        val dy = template.map { it.y.toDouble() }.average()
        var denominator = 0.0
        var dot = 0.0
        var cross = 0.0
        points.zip(template).forEach { (source, target) ->
            val x = source.x - sx; val y = source.y - sy
            val u = target.x - dx; val v = target.y - dy
            denominator += x * x + y * y
            dot += x * u + y * v
            cross += x * v - y * u
        }
        require(denominator > 1e-6) { "Face landmarks are degenerate." }
        val a = dot / denominator
        val b = cross / denominator
        require(a * a + b * b > 1e-8) { "Face alignment failed." }
        return floatArrayOf(a.toFloat(), b.toFloat(), (dx - a * sx + b * sy).toFloat(), (dy - b * sx - a * sy).toFloat())
    }

    fun suppressOverlaps(faces: List<DetectedFace>, threshold: Float = .4f): List<DetectedFace> {
        val kept = mutableListOf<DetectedFace>()
        for (face in faces.sortedByDescending { it.score }) {
            if (kept.none { overlap(face, it) > threshold }) kept += face
        }
        return kept
    }

    private fun overlap(a: DetectedFace, b: DetectedFace): Float {
        val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left) + 1) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top) + 1)
        val union = (a.right - a.left + 1) * (a.bottom - a.top + 1) +
            (b.right - b.left + 1) * (b.bottom - b.top + 1) - intersection
        return if (union > 0) intersection / union else 0f
    }
}
