package com.example.mobile_image_retrieval.ai

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

data class TokenizerTestVector(val text: String, val inputIds: LongArray)

/** Exporter-owned MobileCLIP2-S0 runtime contract. No graph or tokenizer values are guessed. */
data class MobileClipModelConfig(
    val imageModelAsset: String,
    val textModelAsset: String,
    val imageInputName: String,
    val imageOutputName: String,
    val imageInputSize: Int,
    val imageLayout: String,
    val imageMean: FloatArray,
    val imageStd: FloatArray,
    val imageResizeMode: String,
    val imageInterpolation: String,
    val imageCenterCrop: Boolean,
    val divideImageBy255: Boolean,
    val textInputName: String,
    val textOutputName: String,
    val contextLength: Int,
    val tokenizerType: String,
    val tokenizerVocabularyAsset: String,
    val tokenizerMergesAsset: String,
    val tokenizerPattern: String,
    val tokenizerPatternIgnoreCase: Boolean,
    val tokenizerCleaning: List<String>,
    val tokenizerVocabularySize: Int,
    val tokenizerMergeCount: Int,
    val startToken: String,
    val endToken: String,
    val startTokenId: Long,
    val endTokenId: Long,
    val padTokenId: Long,
    val truncateText: Boolean,
    val tokenizerTestVectors: List<TokenizerTestVector>,
    val embeddingDimension: Int,
) {
    companion object {
        const val ASSET = "models/mobileclip2_s0_config.json"
        private const val ASSET_DIRECTORY = "models"

        fun load(assets: AssetManager): MobileClipModelConfig {
            val json = try {
                assets.open(ASSET).bufferedReader().use { JSONObject(it.readText()) }
            } catch (error: Exception) {
                throw ModelUnavailableException(
                    "MobileCLIP2-S0 configuration is missing. Expected $ASSET from the model exporter.",
                    error,
                )
            }
            return try {
                parse(json)
            } catch (error: Exception) {
                throw ModelUnavailableException(
                    "$ASSET does not satisfy the generated MobileCLIP2-S0 export contract: ${error.message}",
                    error,
                )
            }
        }

        private fun parse(root: JSONObject): MobileClipModelConfig {
            require(root.getInt("format_version") == 1) { "unsupported format_version" }
            val model = root.getJSONObject("model")
            val image = root.getJSONObject("image_encoder")
            val preprocessing = image.getJSONObject("preprocessing")
            val text = root.getJSONObject("text_encoder")
            val tokenizer = root.getJSONObject("tokenizer")
            val contextLength = text.getInt("context_length")
            val dimension = model.getInt("embedding_dimension")
            val testVectorsJson = root.getJSONArray("tokenizer_test_vectors")
            val testVectors = List(testVectorsJson.length()) { index ->
                val vector = testVectorsJson.getJSONObject(index)
                TokenizerTestVector(vector.getString("text"), vector.getJSONArray("input_ids").toLongArray())
            }

            val config = MobileClipModelConfig(
                imageModelAsset = modelAsset(image.getString("model_file")),
                textModelAsset = modelAsset(text.getString("model_file")),
                imageInputName = image.getString("input_name"),
                imageOutputName = image.getString("output_name"),
                imageInputSize = image.getInt("image_size"),
                imageLayout = image.getString("layout"),
                imageMean = image.getJSONArray("mean").toFloatArray(3, "image mean"),
                imageStd = image.getJSONArray("std").toFloatArray(3, "image std"),
                imageResizeMode = preprocessing.getString("resize_mode"),
                imageInterpolation = preprocessing.getString("interpolation"),
                imageCenterCrop = preprocessing.getBoolean("center_crop"),
                divideImageBy255 = preprocessing.getBoolean("divide_uint8_by_255"),
                textInputName = text.getString("input_name"),
                textOutputName = text.getString("output_name"),
                contextLength = contextLength,
                tokenizerType = tokenizer.getString("type"),
                tokenizerVocabularyAsset = modelAsset(tokenizer.getString("vocab_file")),
                tokenizerMergesAsset = modelAsset(tokenizer.getString("merges_file")),
                tokenizerPattern = tokenizer.getString("regex"),
                tokenizerPatternIgnoreCase = tokenizer.getBoolean("regex_ignore_case"),
                tokenizerCleaning = tokenizer.getJSONArray("text_cleaning").toStringList(),
                tokenizerVocabularySize = tokenizer.getInt("vocab_size"),
                tokenizerMergeCount = tokenizer.getInt("merge_count"),
                startToken = tokenizer.getString("sot_token"),
                endToken = tokenizer.getString("eot_token"),
                startTokenId = tokenizer.getLong("sot_token_id"),
                endTokenId = tokenizer.getLong("eot_token_id"),
                padTokenId = tokenizer.getLong("padding_value"),
                truncateText = tokenizer.getBoolean("truncate"),
                tokenizerTestVectors = testVectors,
                embeddingDimension = dimension,
            )

            require(model.getString("name") == "MobileCLIP2-S0") { "model.name must be MobileCLIP2-S0" }
            require(model.getBoolean("embeddings_l2_normalized")) { "export must produce normalized embeddings" }
            require(dimension == 512) { "MobileCLIP2-S0 export must produce 512-D embeddings" }
            require(config.imageInputSize == 256) { "this MobileCLIP2-S0 export must use a 256x256 input" }
            require(config.imageLayout == "NCHW") { "only the exported NCHW layout is supported" }
            require(config.imageResizeMode == "shortest") { "image resize_mode must be shortest" }
            require(config.imageInterpolation == "bicubic") { "image interpolation must be bicubic" }
            require(config.imageCenterCrop) { "image preprocessing must enable center_crop" }
            require(config.divideImageBy255) { "image preprocessing must divide uint8 pixels by 255" }
            require(image.getString("input_dtype") == "float32" && image.getString("output_dtype") == "float32") {
                "image graph must use float32 input and output"
            }
            require(image.getJSONArray("input_shape").matches("batch", 3, 256, 256)) { "unexpected image input_shape" }
            require(image.getJSONArray("output_shape").matches("batch", dimension)) { "unexpected image output_shape" }
            require(image.getJSONArray("input_range").matchesNumbers(0.0, 1.0)) { "image input_range must be [0, 1]" }
            require(config.imageStd.all { it.isFinite() && it > 0f }) { "image std values must be positive and finite" }

            require(text.getString("input_dtype") == "int64" && text.getString("output_dtype") == "float32") {
                "text graph must use int64 input and float32 output"
            }
            require(text.getJSONArray("input_shape").matches("batch", contextLength)) { "unexpected text input_shape" }
            require(text.getJSONArray("output_shape").matches("batch", dimension)) { "unexpected text output_shape" }
            require(contextLength == 77 && tokenizer.getInt("context_length") == contextLength) {
                "text and tokenizer context lengths must both be 77"
            }
            require(config.tokenizerType == "open_clip_simple_tokenizer") { "unsupported tokenizer type" }
            require(tokenizer.getString("byte_encoding") == "openai_clip_bytes_to_unicode") { "unsupported byte encoding" }
            require(tokenizer.getString("clean_function") == "_clean_lower") { "unsupported tokenizer clean function" }
            require(!tokenizer.getBoolean("padding_is_reserved_token")) { "unexpected padding token contract" }
            require(config.tokenizerVocabularySize == 49_408) { "unexpected tokenizer vocabulary size" }
            require(config.tokenizerMergeCount > 0) { "tokenizer merge_count must be positive" }
            require(config.truncateText) { "tokenizer must explicitly permit truncation" }
            require(config.tokenizerCleaning == EXPECTED_CLEANING) { "unsupported tokenizer text_cleaning pipeline" }
            require(testVectors.isNotEmpty() && testVectors.all { it.inputIds.size == contextLength }) {
                "tokenizer test vectors must contain exactly $contextLength token IDs"
            }
            return config
        }

        private fun modelAsset(filename: String): String {
            require(filename.isNotBlank() && '/' !in filename && '\\' !in filename) { "model filenames must be local asset names" }
            return "$ASSET_DIRECTORY/$filename"
        }

        private val EXPECTED_CLEANING = listOf(
            "ftfy_fix_text", "html_unescape_twice", "trim", "collapse_whitespace", "lowercase",
        )
    }
}

private fun JSONArray.toFloatArray(expectedSize: Int, label: String): FloatArray {
    require(length() == expectedSize) { "$label must contain $expectedSize values" }
    return FloatArray(length()) { getDouble(it).toFloat() }
}

private fun JSONArray.toLongArray(): LongArray = LongArray(length()) { getLong(it) }
private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

private fun JSONArray.matches(vararg expected: Any): Boolean =
    length() == expected.size && expected.indices.all { index ->
        when (val value = expected[index]) {
            is String -> optString(index) == value
            is Int -> optInt(index, Int.MIN_VALUE) == value
            else -> false
        }
    }

private fun JSONArray.matchesNumbers(vararg expected: Double): Boolean =
    length() == expected.size && expected.indices.all { index -> getDouble(index) == expected[index] }
