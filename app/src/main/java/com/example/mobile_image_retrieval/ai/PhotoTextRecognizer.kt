package com.example.mobile_image_retrieval.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object OcrModelContract {
    const val VERSION = "mlkit-latin-16.0.1-vi-v1"
    const val MAX_CHARACTERS = 100_000
}

fun interface PhotoTextRecognizer {
    suspend fun recognize(bitmap: Bitmap): String
}

class BundledPhotoTextRecognizer : PhotoTextRecognizer {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val mutex = Mutex()

    override suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            // The ML Kit task cannot be cancelled. Await completion before callers recycle its bitmap.
            suspendCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(VietnameseText.normalize(it.text)) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
        }
    }
}
