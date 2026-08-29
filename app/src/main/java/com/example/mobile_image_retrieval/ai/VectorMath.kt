package com.example.mobile_image_retrieval.ai

import kotlin.math.sqrt

object VectorMath {
    fun l2NormalizeInPlace(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) sum += value.toDouble() * value.toDouble()
        require(sum > 0.0 && sum.isFinite()) { "Cannot normalize a zero or non-finite embedding" }
        val inverseNorm = (1.0 / sqrt(sum)).toFloat()
        for (index in vector.indices) vector[index] *= inverseNorm
        return vector
    }

    fun dot(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size) { "Vector dimensions differ: ${left.size} and ${right.size}" }
        var score = 0f
        for (index in left.indices) score += left[index] * right[index]
        return score
    }
}
