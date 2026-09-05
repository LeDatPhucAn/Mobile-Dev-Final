package com.example.mobile_image_retrieval.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingOutputResolverTest {
    @Test
    fun `configured output takes precedence over another compatible tensor`() {
        assertEquals(
            "text_embedding",
            resolve(output("auxiliary"), output("text_embedding")),
        )
    }

    @Test
    fun `renamed text or image embedding resolves by type and shape`() {
        for (configured in listOf("text_embedding", "image_embedding")) {
            assertEquals(
                "exported_output_0",
                EmbeddingOutputResolver.resolve(configured, 512, listOf(output("exported_output_0"))),
            )
        }
    }

    @Test
    fun `fixed single batch output is supported`() {
        assertEquals("output", resolve(output("output", shape = listOf(1L, 512L))))
    }

    @Test
    fun `unrelated output before embedding is ignored`() {
        assertEquals(
            "embedding",
            resolve(output("logits", shape = listOf(1L, 10L)), output("embedding")),
        )
    }

    @Test
    fun `ambiguous renamed outputs fail with the graph names`() {
        val error = assertThrows(ModelInferenceException::class.java) {
            resolve(output("first"), output("second"))
        }
        assertTrue(error.message.orEmpty().contains("first"))
        assertTrue(error.message.orEmpty().contains("second"))
    }

    @Test
    fun `invalid dimensions ranks batch sizes and types are rejected`() {
        val invalidOutputs = listOf(
            output("output", shape = listOf(1L, 768L)),
            output("output", shape = listOf(1L, 77L, 512L)),
            output("output", shape = listOf(512L)),
            output("output", shape = listOf(2L, 512L)),
            output("output", isFloat = false),
        )
        invalidOutputs.forEach { candidate ->
            assertThrows(ModelInferenceException::class.java) { resolve(candidate) }
        }
        assertThrows(ModelInferenceException::class.java) { resolve() }
    }

    @Test
    fun `incompatible configured output must not silently select another model output`() {
        assertThrows(ModelInferenceException::class.java) {
            resolve(output("text_embedding", isFloat = false), output("other"))
        }
    }

    private fun resolve(vararg outputs: EmbeddingOutputSpec) =
        EmbeddingOutputResolver.resolve("text_embedding", 512, outputs.toList())

    private fun output(
        name: String,
        isFloat: Boolean = true,
        shape: List<Long> = listOf(-1L, 512L),
    ) = EmbeddingOutputSpec(name, isFloat, shape)
}
