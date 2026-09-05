package com.example.mobile_image_retrieval.ai

internal data class EmbeddingOutputSpec(
    val name: String,
    val isFloat: Boolean,
    val shape: List<Long>,
)

/** Export tools may rename an output, but its type and embedding shape must still match. */
internal object EmbeddingOutputResolver {
    fun resolve(configuredName: String, dimension: Int, outputs: List<EmbeddingOutputSpec>): String {
        fun EmbeddingOutputSpec.isCompatible() = isFloat &&
            shape.size == 2 && (shape[0] == 1L || shape[0] == -1L) &&
            shape[1] == dimension.toLong()

        val configured = outputs.firstOrNull { it.name == configuredName }
        if (configured != null && configured.isCompatible()) return configured.name
        // A present but incompatible configured output signals a different model contract.
        if (configured == null) {
            outputs.filter { it.isCompatible() }.singleOrNull()?.let { return it.name }
        }
        val available = outputs.joinToString { "${it.name}: ${if (it.isFloat) "FLOAT" else "non-FLOAT"} ${it.shape}" }
        throw ModelInferenceException(
            "Cannot resolve ONNX embedding output '$configuredName'. Expected FLOAT [1 or batch, $dimension]; " +
                "available outputs: $available. Package matching MobileCLIP2-S0 models and config.",
        )
    }
}
