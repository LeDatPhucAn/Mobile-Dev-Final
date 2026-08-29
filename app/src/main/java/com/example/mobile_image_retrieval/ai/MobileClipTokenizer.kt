package com.example.mobile_image_retrieval.ai

import android.content.res.AssetManager
import org.json.JSONObject
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** OpenCLIP SimpleTokenizer byte-BPE, driven and self-tested by the exporter-owned contract. */
class MobileClipTokenizer(assets: AssetManager, private val config: MobileClipModelConfig) {
    private val vocabulary: Map<String, Long>
    private val mergeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder = bytesToUnicode()
    private val cache = ConcurrentHashMap<String, String>()
    private val tokenPattern: Pattern

    init {
        try {
            require(config.tokenizerType == "open_clip_simple_tokenizer")
            val flags = if (config.tokenizerPatternIgnoreCase) Pattern.CASE_INSENSITIVE else 0
            tokenPattern = Pattern.compile(config.tokenizerPattern, flags)

            val vocabularyJson = assets.open(config.tokenizerVocabularyAsset)
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
            vocabulary = vocabularyJson.keys().asSequence().associateWith { vocabularyJson.getLong(it) }

            val merges = assets.open(config.tokenizerMergesAsset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#version:") }
                    .map { line -> line.trim().split(Regex("\\s+")) }
                    .filter { it.size == 2 }
                    .toList()
            }
            mergeRanks = merges.mapIndexed { index, pair -> (pair[0] to pair[1]) to index }.toMap()

            require(vocabulary.size == config.tokenizerVocabularySize) {
                "expected ${config.tokenizerVocabularySize} vocabulary entries, found ${vocabulary.size}"
            }
            require(mergeRanks.size == config.tokenizerMergeCount) {
                "expected ${config.tokenizerMergeCount} BPE merges, found ${mergeRanks.size}"
            }
            require(vocabulary[config.startToken] == config.startTokenId) { "start token ID does not match vocabulary" }
            require(vocabulary[config.endToken] == config.endTokenId) { "end token ID does not match vocabulary" }
            validateExportedTestVectors()
        } catch (error: Exception) {
            throw ModelUnavailableException(
                "Tokenizer assets or behavior do not match ${MobileClipModelConfig.ASSET}: ${error.message}",
                error,
            )
        }
    }

    fun tokenize(text: String): LongArray = tokenizeInternal(text)

    private fun tokenizeInternal(text: String): LongArray {
        val normalized = OpenClipTextCleaner.cleanLower(text)
        val ids = ArrayList<Long>(config.contextLength)
        ids += config.startTokenId
        val matcher = tokenPattern.matcher(normalized)
        while (matcher.find() && ids.size < config.contextLength - 1) {
            val utf8 = matcher.group().toByteArray(Charsets.UTF_8)
            val encoded = buildString(utf8.size) {
                utf8.forEach { byte -> append(byteEncoder[byte.toInt() and 0xff]) }
            }
            for (piece in bpe(encoded).split(' ')) {
                ids += vocabulary[piece]
                    ?: throw ModelUnavailableException("Tokenizer vocabulary has no token for an encoded query piece")
                if (ids.size >= config.contextLength - 1) break
            }
        }
        ids += config.endTokenId
        return LongArray(config.contextLength) { index -> ids.getOrNull(index) ?: config.padTokenId }
    }

    private fun validateExportedTestVectors() {
        config.tokenizerTestVectors.forEachIndexed { index, vector ->
            val actual = tokenizeInternal(vector.text)
            require(actual.contentEquals(vector.inputIds)) {
                val mismatch = actual.indices.firstOrNull { actual[it] != vector.inputIds[it] }
                "tokenizer test vector $index failed at token index $mismatch"
            }
        }
    }

    private fun bpe(token: String): String = cache.getOrPut(token) {
        var word = token.mapIndexed { index, char -> if (index == token.lastIndex) "$char</w>" else char.toString() }
        while (word.size > 1) {
            val best = word.zipWithNext().minByOrNull { mergeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (best !in mergeRanks) break
            val merged = ArrayList<String>(word.size)
            var index = 0
            while (index < word.size) {
                if (index < word.lastIndex && word[index] == best.first && word[index + 1] == best.second) {
                    merged += best.first + best.second
                    index += 2
                } else {
                    merged += word[index++]
                }
            }
            word = merged
        }
        word.joinToString(" ")
    }

    private fun bytesToUnicode(): Array<Char> {
        val visibleBytes = ((33..126) + (161..172) + (174..255)).toMutableList()
        val codePoints = visibleBytes.toMutableList()
        var extra = 0
        for (byte in 0..255) if (byte !in visibleBytes) {
            visibleBytes += byte
            codePoints += 256 + extra++
        }
        val result = Array(256) { '\u0000' }
        visibleBytes.indices.forEach { result[visibleBytes[it]] = codePoints[it].toChar() }
        return result
    }
}

/** Android implementation of OpenCLIP's `_clean_lower` query-cleaning contract. */
internal object OpenClipTextCleaner {
    private val whitespace = Regex("\\s+")
    private val htmlEntity = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[A-Za-z][A-Za-z0-9]+);")
    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00a0", "copy" to "©", "reg" to "®", "trade" to "™",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "lsquo" to "‘",
        "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    )

    fun cleanLower(input: String): String {
        var text = fixText(input)
        repeat(2) { text = htmlUnescape(text) }
        return whitespace.replace(text.trim(), " ").lowercase(Locale.ROOT)
    }

    /**
     * FTFY's essential behavior for search input: NFC normalization plus conservative repair of
     * UTF-8 that was accidentally decoded as Windows-1252. Correct Unicode is left untouched.
     */
    private fun fixText(input: String): String {
        var current = Normalizer.normalize(input, Normalizer.Form.NFC)
        repeat(2) {
            val repaired = repairWindows1252Mojibake(current) ?: return@repeat
            if (mojibakeScore(repaired) < mojibakeScore(current)) current = repaired
        }
        return Normalizer.normalize(current, Normalizer.Form.NFC)
    }

    private fun repairWindows1252Mojibake(text: String): String? {
        return try {
            val windows1252 = charset("windows-1252")
            val encoder = windows1252.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            if (!encoder.canEncode(text)) return null
            val bytes = encoder.encode(java.nio.CharBuffer.wrap(text))
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(bytes)
                .toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun mojibakeScore(text: String): Int = text.sumOf { char ->
        when (char) {
            '\ufffd' -> 10
            'Ã', 'Â', 'Ä', 'â', 'ð' -> 2
            in '\u0080'..'\u009f' -> 2
            else -> 0
        }
    }

    private fun htmlUnescape(input: String): String = htmlEntity.replace(input) { match ->
        val entity = match.groupValues[1]
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)
            entity.startsWith('#') -> entity.drop(1).toIntOrNull()
            else -> null
        }
        when {
            codePoint != null && Character.isValidCodePoint(codePoint) -> String(Character.toChars(codePoint))
            codePoint != null -> match.value
            else -> namedEntities[entity.lowercase(Locale.ROOT)] ?: match.value
        }
    }
}
