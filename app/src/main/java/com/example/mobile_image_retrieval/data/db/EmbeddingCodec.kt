package com.example.mobile_image_retrieval.data.db

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Float32 codec kept behind one API so an explicit FP16 migration can be added later. */
object EmbeddingCodec {
    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(values)
        return buffer.array()
    }

    fun decode(bytes: ByteArray, expectedDimension: Int): FloatArray {
        require(bytes.size == expectedDimension * Float.SIZE_BYTES) {
            "Embedding contains ${bytes.size} bytes; expected ${expectedDimension * Float.SIZE_BYTES}"
        }
        val floats = FloatArray(expectedDimension)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }

    /** Computes against encoded little-endian Float32 values without allocating a candidate vector. */
    fun dot(bytes: ByteArray, query: FloatArray, expectedDimension: Int = query.size): Float {
        require(expectedDimension == query.size)
        require(bytes.size == expectedDimension * Float.SIZE_BYTES)
        var score = 0f
        var offset = 0
        for (index in 0 until expectedDimension) {
            val bits = (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
            score += query[index] * Float.fromBits(bits)
            offset += Float.SIZE_BYTES
        }
        return score
    }
}
