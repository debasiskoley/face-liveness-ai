package com.example.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Packs ArcFace float embeddings into Room BLOB columns. */
object FaceEmbeddingCodec {
    fun encode(values: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) buffer.putFloat(value)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in out.indices) out[i] = buffer.float
        return out
    }
}
