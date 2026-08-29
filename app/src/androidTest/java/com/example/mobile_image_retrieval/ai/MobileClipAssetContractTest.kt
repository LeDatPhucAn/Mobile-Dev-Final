package com.example.mobile_image_retrieval.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileClipAssetContractTest {
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
}
