package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.min

/**
 * Silent-Face anti-spoofing via MiniFASNetV2 ONNX (Minivision / yakhyo).
 *
 * Trained to classify real vs print vs replay. Preprocess: BGR float 0–255,
 * NCHW 80×80, scale 2.7 crop. Class index 1 = Real.
 */
class SilentFaceAntiSpoof private constructor(context: Context) {

    data class Result(
        val isReal: Boolean,
        val realProbability: Float,
        val spoofProbability: Float,
        val label: String,
        val available: Boolean
    )

    private data class Model(
        val session: OrtSession,
        val inputName: String,
        val scale: Float,
        val name: String
    )

    private val env: OrtEnvironment?
    private val model: Model?
    val isAvailable: Boolean

    init {
        val ort = try {
            OrtEnvironment.getEnvironment()
        } catch (t: Throwable) {
            Log.e(TAG, "ORT env failed", t)
            null
        }
        env = ort
        model = if (ort != null) loadModel(context, ort, MODEL_V2, SCALE_V2) else null
        isAvailable = model != null
        Log.i(TAG, "Silent-Face PAD ready model=${model?.name} available=$isAvailable")
    }

    /**
     * @param bitmap camera frame (ARGB)
     * @param normalizedBox face box in 0–1 coords
     */
    fun evaluate(bitmap: Bitmap, normalizedBox: RectF?): Result {
        val loaded = model
        if (!isAvailable || env == null || loaded == null) {
            return Result(
                isReal = true,
                realProbability = 0.5f,
                spoofProbability = 0.5f,
                label = "PAD model missing",
                available = false
            )
        }
        val box = normalizedBox ?: RectF(0.25f, 0.2f, 0.75f, 0.8f)
        if (box.width() < 0.05f || box.height() < 0.05f) {
            return Result(false, 0f, 1f, "Face too small for PAD", true)
        }

        return try {
            val probs = synchronized(this) { runModel(loaded, bitmap, box) }
                ?: return Result(true, 0.5f, 0.5f, "PAD inference empty", false)
            val realP = if (probs.size > REAL_CLASS) probs[REAL_CLASS] else 0f
            val spoofP = when {
                probs.size >= 3 -> probs[0] + probs[2]
                else -> 1f - realP
            }
            val pred = probs.indices.maxByOrNull { probs[it] } ?: 0
            val isReal = pred == REAL_CLASS && realP >= REAL_THRESHOLD
            val label = if (isReal) {
                "Live face (MiniFASNet ${"%.0f".format(realP * 100)}%)"
            } else {
                "SPOOF / REPLAY (MiniFASNet ${"%.0f".format(spoofP * 100)}%)"
            }
            Log.i(TAG, "$label | real=${"%.2f".format(realP)} spoof=${"%.2f".format(spoofP)} pred=$pred")
            Result(
                isReal = isReal,
                realProbability = realP.coerceIn(0f, 1f),
                spoofProbability = spoofP.coerceIn(0f, 1f),
                label = label,
                available = true
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Silent-Face evaluate failed", t)
            Result(true, 0.5f, 0.5f, "PAD error", false)
        }
    }

    private fun runModel(model: Model, bitmap: Bitmap, box: RectF): FloatArray? {
        val ortEnv = env ?: return null
        val crop = cropScaled(bitmap, box, model.scale, INPUT) ?: return null
        val buffer = bgrNchwBuffer(crop)
        return OnnxTensor.createTensor(ortEnv, buffer, longArrayOf(1, 3, INPUT.toLong(), INPUT.toLong())).use { tensor ->
            model.session.run(mapOf(model.inputName to tensor)).use { results ->
                val raw = results[0].value
                val logits = flatten(raw) ?: return@use null
                softmax(logits)
            }
        }
    }

    companion object {
        private const val TAG = "SilentFaceAntiSpoof"
        const val MODEL_V2 = "MiniFASNetV2.onnx"
        private const val SCALE_V2 = 2.7f
        private const val INPUT = 80
        private const val REAL_CLASS = 1
        private const val REAL_THRESHOLD = 0.65f

        @Volatile
        private var instance: SilentFaceAntiSpoof? = null

        fun getInstance(context: Context): SilentFaceAntiSpoof {
            return instance ?: synchronized(this) {
                instance ?: SilentFaceAntiSpoof(context.applicationContext).also { instance = it }
            }
        }

        private fun loadModel(
            context: Context,
            env: OrtEnvironment,
            assetName: String,
            scale: Float
        ): Model? {
            return try {
                context.assets.open(assetName).use { /* exists */ }
                val file = copyAsset(context, assetName)
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val session = env.createSession(file.absolutePath, options)
                val inputName = session.inputNames.first()
                Model(session, inputName, scale, assetName.removeSuffix(".onnx"))
            } catch (t: Throwable) {
                Log.i(TAG, "Skip $assetName: ${t.message}")
                null
            }
        }

        private fun copyAsset(context: Context, assetName: String): File {
            val dir = File(context.filesDir, "onnx_models")
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, assetName)
            val expected = try {
                context.assets.openFd(assetName).use { it.length }
            } catch (_: Exception) {
                -1L
            }
            if (out.exists() && out.length() > 100_000L && (expected < 0L || out.length() == expected)) {
                return out
            }
            context.assets.open(assetName).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            return out
        }

        private fun cropScaled(bitmap: Bitmap, box: RectF, scale: Float, outSize: Int): Bitmap? {
            val srcW = bitmap.width
            val srcH = bitmap.height
            if (srcW < 8 || srcH < 8) return null
            val bw = (box.width() * srcW).coerceAtLeast(8f)
            val bh = (box.height() * srcH).coerceAtLeast(8f)
            val applied = min(min((srcH - 1) / bh, (srcW - 1) / bw), scale)
            val newW = bw * applied
            val newH = bh * applied
            val cx = box.centerX() * srcW
            val cy = box.centerY() * srcH
            val left = (cx - newW / 2f).toInt().coerceIn(0, srcW - 2)
            val top = (cy - newH / 2f).toInt().coerceIn(0, srcH - 2)
            val right = (cx + newW / 2f).toInt().coerceIn(left + 2, srcW)
            val bottom = (cy + newH / 2f).toInt().coerceIn(top + 2, srcH)
            val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            return Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
        }

        /** BGR channels, float 0–255, NCHW — matches yakhyo ONNX inference. */
        private fun bgrNchwBuffer(bitmap: Bitmap): java.nio.FloatBuffer {
            val h = bitmap.height
            val w = bitmap.width
            val b = FloatArray(h * w)
            val g = FloatArray(h * w)
            val r = FloatArray(h * w)
            var i = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val c = bitmap.getPixel(x, y)
                    r[i] = Color.red(c).toFloat()
                    g[i] = Color.green(c).toFloat()
                    b[i] = Color.blue(c).toFloat()
                    i++
                }
            }
            val buffer = ByteBuffer.allocateDirect(1 * 3 * h * w * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(b)
            buffer.put(g)
            buffer.put(r)
            buffer.rewind()
            return buffer
        }

        private fun flatten(value: Any?): FloatArray? {
            return when (value) {
                is FloatArray -> value
                is Array<*> -> {
                    val first = value.firstOrNull() ?: return null
                    when (first) {
                        is FloatArray -> first
                        is Array<*> -> flatten(first)
                        else -> null
                    }
                }
                else -> null
            }
        }

        private fun softmax(logits: FloatArray): FloatArray {
            var max = Float.NEGATIVE_INFINITY
            for (v in logits) if (v > max) max = v
            var sum = 0f
            val out = FloatArray(logits.size)
            for (i in logits.indices) {
                out[i] = exp((logits[i] - max).toDouble()).toFloat()
                sum += out[i]
            }
            if (sum <= 0f) return out
            for (i in out.indices) out[i] /= sum
            return out
        }
    }
}
