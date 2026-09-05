package com.example.mobile_image_retrieval.ai

import java.text.Normalizer
import java.util.Locale

object VietnameseText {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    /** Used only in the search index; original OCR text and keyboard composition retain accents. */
    fun searchable(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT).replace('đ', 'd')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun ftsQuery(text: String): String? {
        val tokens = searchable(text).split(' ').filter { it.isNotEmpty() }.distinct()
        require(tokens.size <= 64) { "Use at most 64 words for text search." }
        // Treat user input as literal words, never as FTS operators or column expressions.
        // Whitespace is conjunction in both standard and enhanced FTS4 query syntax.
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it\"" }
    }
}
