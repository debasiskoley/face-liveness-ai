package com.example

import com.example.data.local.FaceEmbeddingCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceEmbeddingCodecTest {

    @Test
    fun `round trips float embeddings`() {
        val original = listOf(0.5f, -0.25f, 0f, 1.0f, -1.0f)
        val encoded = FaceEmbeddingCodec.encode(original)
        assertEquals(original.size * Float.SIZE_BYTES, encoded.size)
        assertArrayEquals(original.toFloatArray(), FaceEmbeddingCodec.decode(encoded), 0f)
    }
}
