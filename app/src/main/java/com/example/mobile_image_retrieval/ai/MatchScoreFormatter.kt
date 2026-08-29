package com.example.mobile_image_retrieval.ai

import kotlin.math.roundToInt

/** UI relevance indicator derived from cosine similarity; it is not probability or confidence. */
object MatchScoreFormatter {
    fun percentage(rawSimilarity: Float): Int = (((rawSimilarity.coerceIn(-1f, 1f) + 1f) * 50f).roundToInt())
}
