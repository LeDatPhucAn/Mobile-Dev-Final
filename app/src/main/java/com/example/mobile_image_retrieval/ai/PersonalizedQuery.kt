package com.example.mobile_image_retrieval.ai

import java.text.Normalizer
import java.util.Locale

data class PersonalizedQuery(val text: String, val handles: List<String>)

object PersonNames {
    private val validHandle = Regex("[\\p{L}\\p{N}_]{1,40}")

    fun handle(name: String): String {
        val normalized = Normalizer.normalize(name.trim().removePrefix("@"), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT).replace(Regex("\\s+"), "_")
        require(validHandle.matches(normalized)) {
            "Use 1–40 letters, numbers or underscores for the name. Spaces become underscores."
        }
        return normalized
    }
}

object PersonalizedQueryParser {
    // Do not interpret email addresses as people mentions. Punctuation can surround a mention.
    private val mention = Regex("(?<![\\p{L}\\p{N}_@])@([\\p{L}\\p{M}\\p{N}_]+)")

    fun parse(query: String): PersonalizedQuery {
        val handles = mention.findAll(query).map { PersonNames.handle(it.groupValues[1]) }.distinct().toList()
        val text = mention.replace(query, " ").replace(Regex("\\s+"), " ").trim()
            .trim(',', '.', '!', '?', ';', ':').trim()
        require(!text.contains(Regex("(?<!\\S)@"))) { "Enter a saved name after @." }
        return PersonalizedQuery(text, handles)
    }
}

/** Gives text and the visual references equal total weight, irrespective of reference count. */
object QueryEmbeddingComposer {
    fun compose(text: FloatArray?, references: List<FloatArray>): FloatArray {
        val vectors = listOfNotNull(text) + references
        require(vectors.isNotEmpty()) { "Enter a prompt or choose a reference photo." }
        val dimension = vectors.first().size
        require(dimension > 0 && vectors.all { it.size == dimension }) { "Reference embedding dimensions differ." }
        val result = FloatArray(dimension)
        fun add(vector: FloatArray, weight: Float) {
            val normalized = VectorMath.l2NormalizeInPlace(vector.copyOf())
            for (i in result.indices) result[i] += normalized[i] * weight
        }
        text?.let { add(it, if (references.isEmpty()) 1f else 0.5f) }
        val weight = (if (text == null) 1f else 0.5f) / references.size.coerceAtLeast(1)
        references.forEach { add(it, weight) }
        return VectorMath.l2NormalizeInPlace(result)
    }
}
