package com.example.mobile_image_retrieval.ai

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.floor

data class RecognizedFace(val detection: DetectedFace, val embedding: FloatArray)

interface FaceAnalyzer {
    suspend fun analyze(bitmap: Bitmap): List<RecognizedFace>
}

/** SCRFD 500M detector + MobileFaceNet, with RGB preprocessing and five-point face alignment. */
class OnnxFaceAnalyzer(private val context: Context) : FaceAnalyzer {
    private val environment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var detector: OrtSession? = null
    private var recognizer: OrtSession? = null

    override suspend fun analyze(bitmap: Bitmap): List<RecognizedFace> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val detectionSession = detector ?: load(FaceModelContract.DETECTOR_ASSET).also { detector = it }
            val recognitionSession = recognizer ?: load(FaceModelContract.RECOGNIZER_ASSET).also { recognizer = it }
            val faces = detect(bitmap, detectionSession)
            require(faces.size <= 64) { "Too many faces in this photo. Use a closer photo." }
            val pixels = IntArray(bitmap.width * bitmap.height)
            if (faces.isNotEmpty()) bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            faces.map { face ->
                currentCoroutineContext().ensureActive()
                RecognizedFace(face, recognize(pixels, bitmap.width, bitmap.height, face, recognitionSession))
            }
        }
    }

    private suspend fun load(asset: String): OrtSession = withContext(Dispatchers.IO) {
        try {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                val bytes = context.assets.open(asset).use { it.readBytes() }
                val expected = if (asset == FaceModelContract.DETECTOR_ASSET) "5e4447f50245bbd7966bd6c0fa52938c61474a04ec7def48753668a9d8b4ea3a" else "9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f"
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                check(actual == expected) { "Face model checksum does not match the supported export." }
                environment.createSession(bytes, options)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw ModelUnavailableException("Face model is unavailable or invalid: $asset", error)
        }
    }

    private fun detect(bitmap: Bitmap, session: OrtSession): List<DetectedFace> {
        val size = 640
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val width = (bitmap.width * scale).toInt().coerceIn(1, size)
        val height = (bitmap.height * scale).toInt().coerceIn(1, size)
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        try { resized.getPixels(pixels, 0, width, 0, 0, width, height) }
        finally { if (resized !== bitmap) resized.recycle() }
        val plane = size * size
        val input = FloatArray(3 * plane) { -127.5f / 128f }
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val index = y * size + x
            input[index] = (((pixel ushr 16) and 255) - 127.5f) / 128f
            input[plane + index] = (((pixel ushr 8) and 255) - 127.5f) / 128f
            input[2 * plane + index] = ((pixel and 255) - 127.5f) / 128f
        }
        val outputs = run(session, input, size)
        require(outputs.size == 9) { "Expected SCRFD's nine score, box and landmark outputs." }
        val candidates = mutableListOf<DetectedFace>()
        for ((level, stride) in listOf(8, 16, 32).withIndex()) {
            val grid = size / stride
            val count = grid * grid * 2
            val scores = outputs[level]; val boxes = outputs[level + 3]; val landmarks = outputs[level + 6]
            require(scores.size == count && boxes.size == count * 4 && landmarks.size == count * 10) { "Unexpected SCRFD output shape." }
            for (anchor in 0 until count) {
                if (scores[anchor] < .5f || !scores[anchor].isFinite()) continue
                val x = ((anchor / 2) % grid) * stride.toFloat()
                val y = ((anchor / 2) / grid) * stride.toFloat()
                val left = (x - boxes[anchor * 4] * stride) / scale
                val top = (y - boxes[anchor * 4 + 1] * stride) / scale
                val right = (x + boxes[anchor * 4 + 2] * stride) / scale
                val bottom = (y + boxes[anchor * 4 + 3] * stride) / scale
                if (right - left < 20 || bottom - top < 20 || left >= bitmap.width || top >= bitmap.height || right <= 0 || bottom <= 0) continue
                val points = (0..4).map { point -> FacePoint(
                    (x + landmarks[anchor * 10 + point * 2] * stride) / scale,
                    (y + landmarks[anchor * 10 + point * 2 + 1] * stride) / scale,
                ) }
                if (points.any { !it.x.isFinite() || !it.y.isFinite() }) continue
                candidates += DetectedFace(left, top, right, bottom, scores[anchor], points)
            }
        }
        return FaceGeometry.suppressOverlaps(candidates)
    }

    private fun recognize(pixels: IntArray, width: Int, height: Int, face: DetectedFace, session: OrtSession): FloatArray {
        val (a, b, tx, ty) = FaceGeometry.alignment(face.landmarks)
        val denominator = a * a + b * b
        val size = 112
        val plane = size * size
        val input = FloatArray(plane * 3)
        fun channel(x: Int, y: Int, shift: Int): Float =
            if (x in 0 until width && y in 0 until height) ((pixels[y * width + x] ushr shift) and 255).toFloat() else 0f
        for (y in 0 until size) for (x in 0 until size) {
            val u = x - tx; val v = y - ty
            val sourceX = (a * u + b * v) / denominator
            val sourceY = (-b * u + a * v) / denominator
            val x0 = floor(sourceX).toInt(); val y0 = floor(sourceY).toInt()
            val fx = sourceX - x0; val fy = sourceY - y0
            for (c in 0..2) {
                val shift = (2 - c) * 8
                val value = channel(x0, y0, shift) * (1 - fx) * (1 - fy) +
                    channel(x0 + 1, y0, shift) * fx * (1 - fy) +
                    channel(x0, y0 + 1, shift) * (1 - fx) * fy + channel(x0 + 1, y0 + 1, shift) * fx * fy
                input[c * plane + y * size + x] = (value - 127.5f) / 127.5f
            }
        }
        val outputs = run(session, input, size)
        require(outputs.size == 1 && outputs[0].size == FaceModelContract.DIMENSION) { "Expected a 512-D MobileFaceNet embedding." }
        return VectorMath.l2NormalizeInPlace(outputs.single())
    }

    private fun run(session: OrtSession, input: FloatArray, size: Int): List<FloatArray> {
        val name = session.inputNames.single()
        val info = session.inputInfo[name]?.info as? TensorInfo
        require(info?.type == OnnxJavaType.FLOAT && info.shape.size == 4) { "Face model requires a Float32 NCHW input." }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
            session.run(mapOf(name to tensor)).use { result ->
                return (0 until result.size()).map { index ->
                    val output = result[index] as? OnnxTensor ?: error("Face model output is not a tensor.")
                    require(output.info.type == OnnxJavaType.FLOAT) { "Face model output is not Float32." }
                    val buffer = output.floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        }
    }
}
