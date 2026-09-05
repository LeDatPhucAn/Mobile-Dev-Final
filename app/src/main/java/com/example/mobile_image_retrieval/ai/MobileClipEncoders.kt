package com.example.mobile_image_retrieval.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

private const val IMAGE_MODEL = "models/mobileclip2_s0_image.onnx"
private const val TEXT_MODEL = "models/mobileclip2_s0_text.onnx"

abstract class MobileClipSession(
    private val context: Context,
    private val modelAsset: String,
) {
    protected val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val loadMutex = Mutex()
    protected val inferenceMutex = Mutex()
    @Volatile private var session: OrtSession? = null

    protected suspend fun session(): OrtSession = session ?: loadMutex.withLock {
        session ?: withContext(Dispatchers.IO) {
            try {
                context.assets.openFd(modelAsset).use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                        val mappedModel = channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            descriptor.startOffset,
                            descriptor.declaredLength,
                        )
                        OrtSession.SessionOptions().use { options ->
                            options.setIntraOpNumThreads(2)
                            options.setInterOpNumThreads(1)
                            environment.createSession(mappedModel, options)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw ModelUnavailableException(
                    "Required model asset is unavailable, compressed, or invalid: $modelAsset",
                    error,
                )
            }
        }.also { session = it }
    }

    protected fun outputVector(result: OrtSession.Result, outputName: String, dimension: Int): FloatArray {
        val value = result.get(outputName).orElseThrow {
            ModelInferenceException("ONNX output '$outputName' was not produced")
        }.value
        val vector = when (value) {
            is Array<*> -> (value.firstOrNull() as? FloatArray)?.copyOf()
            is FloatArray -> value.copyOf()
            else -> null
        } ?: throw ModelInferenceException("ONNX output '$outputName' is not a float embedding")
        if (vector.size != dimension) throw ModelInferenceException("Expected $dimension-D output, received ${vector.size}")
        return VectorMath.l2NormalizeInPlace(vector)
    }
}

class MobileClipImageEncoder(private val context: Context) : MobileClipSession(context, IMAGE_MODEL), ImageEmbeddingModel {
    @Volatile private var loadedConfig: MobileClipModelConfig? = null

    override suspend fun embed(bitmap: android.graphics.Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val config = loadedConfig ?: MobileClipModelConfig.load(context.assets).also { loadedConfig = it }
        if (config.imageModelAsset != IMAGE_MODEL) {
            throw ModelUnavailableException("Configured image model does not match the packaged MobileCLIP2-S0 asset")
        }
        val preprocessStarted = SystemClock.elapsedRealtime()
        val prepared = MobileClipPreprocessor(config).prepare(bitmap)
        Log.d("MobileCLIP", "image preprocess: ${SystemClock.elapsedRealtime() - preprocessStarted} ms")
        inferenceMutex.withLock {
            val inferenceStarted = SystemClock.elapsedRealtime()
            val ortSession = session()
            val outputName = embeddingOutputName(ortSession, config.imageInputName, config.imageOutputName, config.embeddingDimension)
            val imageInfo = ortSession.inputInfo[config.imageInputName]?.info as? TensorInfo
            if (imageInfo?.type != OnnxJavaType.FLOAT) throw ModelInferenceException("Image input must be FLOAT, graph reports ${imageInfo?.type}")
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(prepared.values), prepared.shape).use { tensor ->
                ortSession.run(mapOf(config.imageInputName to tensor)).use { result ->
                    outputVector(result, outputName, config.embeddingDimension).also {
                        Log.d("MobileCLIP", "image inference: ${SystemClock.elapsedRealtime() - inferenceStarted} ms")
                    }
                }
            }
        }
    }
}

class MobileClipTextEncoder(private val context: Context) : MobileClipSession(context, TEXT_MODEL), TextEmbeddingModel {
    @Volatile private var loadedConfig: MobileClipModelConfig? = null
    @Volatile private var loadedTokenizer: MobileClipTokenizer? = null

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "Search query cannot be blank" }
        val config = loadedConfig ?: MobileClipModelConfig.load(context.assets).also { loadedConfig = it }
        if (config.textModelAsset != TEXT_MODEL) {
            throw ModelUnavailableException("Configured text model does not match the packaged MobileCLIP2-S0 asset")
        }
        val tokenizer = loadedTokenizer ?: MobileClipTokenizer(context.assets, config).also { loadedTokenizer = it }
        val tokenIds = tokenizer.tokenize(text)
        inferenceMutex.withLock {
            val inferenceStarted = SystemClock.elapsedRealtime()
            val ortSession = session()
            val outputName = embeddingOutputName(ortSession, config.textInputName, config.textOutputName, config.embeddingDimension)
            val inputs = LinkedHashMap<String, OnnxTensor>()
            try {
                inputs[config.textInputName] = integerTensor(ortSession, config.textInputName, tokenIds, config.contextLength)
                ortSession.run(inputs).use { result ->
                    outputVector(result, outputName, config.embeddingDimension).also {
                        Log.d("MobileCLIP", "text inference: ${SystemClock.elapsedRealtime() - inferenceStarted} ms")
                    }
                }
            } finally {
                inputs.values.forEach(OnnxTensor::close)
            }
        }
    }

    private fun integerTensor(session: OrtSession, name: String, values: LongArray, contextLength: Int): OnnxTensor {
        val info = session.inputInfo[name]?.info as? TensorInfo
            ?: throw ModelInferenceException("Configured input '$name' is not a tensor")
        val shape = longArrayOf(1, contextLength.toLong())
        return when (info.type) {
            OnnxJavaType.INT64 -> OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape)
            else -> throw ModelInferenceException("Text input '$name' must be INT64, graph reports ${info.type}")
        }
    }
}

private fun embeddingOutputName(session: OrtSession, inputName: String, outputName: String, dimension: Int): String {
    if (inputName !in session.inputNames) throw ModelInferenceException("Configured input '$inputName' is absent from the ONNX graph")
    return EmbeddingOutputResolver.resolve(
        outputName,
        dimension,
        session.outputInfo.map { (name, node) ->
            val info = node.info as? TensorInfo
            EmbeddingOutputSpec(name, info?.type == OnnxJavaType.FLOAT, info?.shape?.toList().orEmpty())
        },
    )
}

object MobileClipAssets {
    fun unavailableReason(context: Context): String? = try {
        val config = MobileClipModelConfig.load(context.assets)
        val required = listOf(
            config.imageModelAsset,
            config.textModelAsset,
            config.tokenizerVocabularyAsset,
            config.tokenizerMergesAsset,
        )
        required.firstOrNull { asset ->
            try { context.assets.open(asset).close(); false } catch (_: Exception) { true }
        }?.let { "Required MobileCLIP2-S0 asset is missing: $it" }
    } catch (error: Exception) {
        error.message ?: "MobileCLIP2-S0 assets are unavailable"
    }
}
