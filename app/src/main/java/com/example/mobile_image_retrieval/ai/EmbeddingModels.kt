package com.example.mobile_image_retrieval.ai

import android.graphics.Bitmap

interface ImageEmbeddingModel {
    suspend fun embed(bitmap: Bitmap): FloatArray
}

interface TextEmbeddingModel {
    suspend fun embed(text: String): FloatArray
}

class ModelUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
class ModelInferenceException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
