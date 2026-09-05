package com.example.mobile_image_retrieval.ai

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileClipAssetContractTest {
    @Test
    fun packagedGraphsProduceNormalizedImageAndTextEmbeddings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textEmbedding = MobileClipTextEncoder(context).embed("a photo of a blue sky")
        assertEmbedding(textEmbedding)

        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            assertEmbedding(MobileClipImageEncoder(context).embed(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun packagedConfigAndTokenizerMatchExporterVectors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = MobileClipModelConfig.load(context.assets)
        val tokenizer = MobileClipTokenizer(context.assets, config)

        assertEquals(512, config.embeddingDimension)
        assertEquals(256, config.imageInputSize)
        assertEquals(77, config.contextLength)
        assertTrue(config.tokenizerTestVectors.isNotEmpty())
        config.tokenizerTestVectors.forEach { vector ->
            assertTrue(tokenizer.tokenize(vector.text).contentEquals(vector.inputIds))
        }
    }

    private fun assertEmbedding(embedding: FloatArray) {
        assertEquals(512, embedding.size)
        assertTrue(embedding.all { it.isFinite() })
        assertEquals(1.0, embedding.sumOf { it.toDouble() * it.toDouble() }, 0.0001)
    }
}
