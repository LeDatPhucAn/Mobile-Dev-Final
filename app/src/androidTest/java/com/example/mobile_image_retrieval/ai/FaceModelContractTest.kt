package com.example.mobile_image_retrieval.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FaceModelContractTest {
    @Test fun bundledModelsDetectAlignAndRecognizeAtDifferentSizes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val analyzer = OnnxFaceAnalyzer(context)
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("astronaut.png").use { BitmapFactory.decodeStream(it)!! }
        val smaller = Bitmap.createScaledBitmap(fixture, 384, 384, true)
        val pair = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val blank = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            val original = analyzer.analyze(fixture).single()
            val resized = analyzer.analyze(smaller).single()
            assertEquals(512, original.embedding.size)
            assertEquals(1f, VectorMath.dot(original.embedding, original.embedding), .0001f)
            assertTrue("The same face at a different size should match", VectorMath.dot(original.embedding, resized.embedding) > FaceModelContract.MATCH_THRESHOLD)
            Canvas(pair).apply { drawBitmap(fixture, 0f, 0f, null); drawBitmap(fixture, 512f, 0f, null) }
            assertEquals(2, analyzer.analyze(pair).size)
            blank.eraseColor(Color.BLUE)
            assertTrue(analyzer.analyze(blank).isEmpty())
        } finally { fixture.recycle(); smaller.recycle(); pair.recycle(); blank.recycle() }
    }
}
