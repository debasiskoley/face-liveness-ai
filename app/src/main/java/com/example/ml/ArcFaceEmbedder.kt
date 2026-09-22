package com.example.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.model.BiometricVector
import com.example.model.EnrolledProfile
import com.example.model.IdentityMatchResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Offline ArcFace identity embedder using InsightFace ONNX weights (`w600k_mbf.onnx`).
 *
 * Input is a 112×112 RGB face, NCHW, (pixel - 127.5) / 127.5. Output is L2-normalized 512-D.
 * MediaPipe is not used for identity — only for liveness in the rest of the app.
 */
class ArcFaceEmbedder private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val env: OrtEnvironment?
    private val session: OrtSession?
    private val inputName: String
    private val nchw: Boolean
    private val inputSize: Int
    private val embeddingSize: Int
    private val matchThreshold: Float
    private val usesOnnx: Boolean
    private val modelName: String

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    init {
        val loaded = loadOnnx(appContext)
        env = loaded?.env
        session = loaded?.session
        inputName = loaded?.inputName ?: "input.1"
        nchw = loaded?.nchw ?: true
        inputSize = loaded?.inputSize ?: FALLBACK_INPUT
        embeddingSize = loaded?.embeddingSize ?: FALLBACK_EMBED_SIZE
        usesOnnx = session != null
        modelName = loaded?.modelName ?: "fallback"
        matchThreshold = if (usesOnnx) ONNX_MATCH_THRESHOLD else FALLBACK_MATCH_THRESHOLD
        Log.i(
            TAG,
            "Identity embedder ready onnx=$usesOnnx model=$modelName dim=$embeddingSize input=$inputSize nchw=$nchw"
        )
    }

    suspend fun embedFromBitmap(bitmap: Bitmap): BiometricVector? = withContext(Dispatchers.Default) {
        val software = softwareBitmap(bitmap) ?: return@withContext null
        val scaled = downscaleForDetect(software)
        val aligned = alignDetectedFace(scaled) ?: return@withContext null
        embedCrop(aligned)
    }

    fun embedAlignedCrop(
        crop: Bitmap,
        faceBox: RectF? = null,
        meshPoints: List<PointF> = emptyList()
    ): BiometricVector? {
        val aligned = cropFace(crop, faceBox, inputSize) ?: return null
        return embedCrop(aligned)
    }

    private fun embedCrop(aligned: Bitmap): BiometricVector? {
        val square = if (aligned.width == inputSize && aligned.height == inputSize) {
            aligned
        } else {
            Bitmap.createScaledBitmap(aligned, inputSize, inputSize, true)
        }
        val features = synchronized(this) {
            if (session != null && env != null) runOnnx(square) else runFallbackDescriptor(square)
        } ?: return null
        if (features.size < MIN_TEMPLATE_SIZE) {
            Log.w(TAG, "Embedding too small dim=${features.size}")
            return null
        }
        return BiometricVector(l2Normalize(features))
    }

    suspend fun match(
        liveBitmap: Bitmap?,
        liveFaceBox: RectF? = null,
        profiles: List<EnrolledProfile>,
        meshPoints: List<PointF> = emptyList()
    ): IdentityMatchResult = withContext(Dispatchers.Default) {
        val templates = profiles.filter { BiometricVector.isIdentityTemplate(it.vector) }
        if (templates.isEmpty()) {
            return@withContext IdentityMatchResult(
                statusLabel = "No enrolled face templates"
            )
        }
        if (liveBitmap == null) {
            return@withContext IdentityMatchResult(
                statusLabel = "No live capture"
            )
        }

        // Same ML Kit + ArcFace path as enrollment. Also try a mirrored crop so
        // front-camera selfies still match CameraX live frames.
        val liveVector = embedFromBitmap(liveBitmap)
            ?: return@withContext IdentityMatchResult(statusLabel = "Live face not clear")
        val flippedVector = embedFromBitmap(flipHorizontal(liveBitmap))

        var best: EnrolledProfile? = null
        var bestScore = -1f
        for (profile in templates) {
            if (profile.vector.features.size != liveVector.features.size) {
                Log.w(
                    TAG,
                    "Skip ${profile.name}: template dim=${profile.vector.features.size} live dim=${liveVector.features.size}"
                )
                continue
            }
            val direct = liveVector.cosineSimilarity(profile.vector)
            val flipped = flippedVector?.let {
                if (it.features.size == profile.vector.features.size) it.cosineSimilarity(profile.vector) else null
            } ?: -1f
            val score = maxOf(direct, flipped)
            Log.i(TAG, "cosine ${profile.name} direct=$direct flip=$flipped best=$score onnx=$usesOnnx")
            if (score > bestScore) {
                bestScore = score
                best = profile
            }
        }

        val isMatch = best != null && bestScore >= matchThreshold
        val percent = ((bestScore.coerceIn(0f, 1f)) * 100).toInt()
        IdentityMatchResult(
            isMatch = isMatch,
            profile = if (isMatch) best else null,
            cosineSimilarity = bestScore.coerceAtLeast(0f),
            matchPercentage = percent,
            statusLabel = when {
                isMatch -> "Matched ${best?.name}"
                best != null -> "Not enrolled (best ${percent}%)"
                else -> "Not enrolled"
            }
        )
    }

    private suspend fun alignDetectedFace(bitmap: Bitmap): Bitmap? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            val five = face?.let { fivePointsFromMlKit(it) }
            if (five != null) {
                warpToArcFace(bitmap, orderArcFacePoints(five), inputSize)
            } else {
                val box = face?.boundingBox?.let { rect ->
                    RectF(
                        rect.left / bitmap.width.toFloat(),
                        rect.top / bitmap.height.toFloat(),
                        rect.right / bitmap.width.toFloat(),
                        rect.bottom / bitmap.height.toFloat()
                    )
                }
                cropFace(bitmap, box, inputSize)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Face detect on enroll photo failed", t)
            cropFace(bitmap, null, inputSize)
        }
    }

    private fun runOnnx(aligned: Bitmap): FloatArray? {
        val ortEnv = env ?: return null
        val ortSession = session ?: return null
        return try {
            val buffer = bitmapToOnnxBuffer(aligned, nchw)
            val shape = if (nchw) {
                longArrayOf(1, 3, aligned.height.toLong(), aligned.width.toLong())
            } else {
                longArrayOf(1, aligned.height.toLong(), aligned.width.toLong(), 3)
            }
            OnnxTensor.createTensor(ortEnv, buffer, shape).use { tensor ->
                ortSession.run(mapOf(inputName to tensor)).use { results ->
                    val raw = results[0].value
                    val embedding = extractEmbedding(raw)
                    if (embedding == null) {
                        Log.w(TAG, "Could not parse ONNX output ${raw?.javaClass?.name}")
                    }
                    embedding
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ONNX ArcFace inference failed", t)
            null
        }
    }

    private fun runFallbackDescriptor(aligned: Bitmap): FloatArray {
        val size = aligned.width.coerceAtMost(aligned.height)
        val gray = IntArray(size * size)
        val rMean = FloatArray(GRID * GRID)
        val gMean = FloatArray(GRID * GRID)
        val bMean = FloatArray(GRID * GRID)
        val cell = size / GRID
        val counts = IntArray(GRID * GRID)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val c = aligned.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                gray[y * size + x] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                val cx = (x / cell).coerceAtMost(GRID - 1)
                val cy = (y / cell).coerceAtMost(GRID - 1)
                val idx = cy * GRID + cx
                rMean[idx] += r
                gMean[idx] += g
                bMean[idx] += b
                counts[idx]++
            }
        }

        equalizeInPlace(gray, size)

        val lbp = FloatArray(GRID * GRID * LBP_BINS)
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val center = gray[y * size + x]
                var code = 0
                if (gray[(y - 1) * size + (x - 1)] >= center) code = code or 1
                if (gray[(y - 1) * size + x] >= center) code = code or 2
                if (gray[(y - 1) * size + (x + 1)] >= center) code = code or 4
                if (gray[y * size + (x + 1)] >= center) code = code or 8
                if (gray[(y + 1) * size + (x + 1)] >= center) code = code or 16
                if (gray[(y + 1) * size + x] >= center) code = code or 32
                if (gray[(y + 1) * size + (x - 1)] >= center) code = code or 64
                if (gray[y * size + (x - 1)] >= center) code = code or 128
                val bin = uniformLbpBin(code)
                val cx = (x / cell).coerceAtMost(GRID - 1)
                val cy = (y / cell).coerceAtMost(GRID - 1)
                lbp[(cy * GRID + cx) * LBP_BINS + bin] += 1f
            }
        }

        val features = FloatArray(FALLBACK_EMBED_SIZE)
        var o = 0
        for (i in lbp.indices) {
            features[o++] = lbp[i]
        }
        for (i in 0 until GRID * GRID) {
            val n = counts[i].coerceAtLeast(1).toFloat()
            features[o++] = (rMean[i] / n) / 255f
            features[o++] = (gMean[i] / n) / 255f
            features[o++] = (bMean[i] / n) / 255f
        }
        return features
    }

    private data class LoadedOnnx(
        val env: OrtEnvironment,
        val session: OrtSession,
        val inputName: String,
        val nchw: Boolean,
        val inputSize: Int,
        val embeddingSize: Int,
        val modelName: String
    )

    companion object {
        private const val TAG = "ArcFaceEmbedder"
        const val MODEL_MBF = "w600k_mbf.onnx"
        const val MIN_TEMPLATE_SIZE = 128
        private const val FALLBACK_INPUT = 112
        private const val GRID = 8
        private const val LBP_BINS = 10
        private const val FALLBACK_EMBED_SIZE = GRID * GRID * LBP_BINS + GRID * GRID * 3
        /** Yakhyo / InsightFace 1:N cosine threshold. */
        private const val ONNX_MATCH_THRESHOLD = 0.40f
        private const val FALLBACK_MATCH_THRESHOLD = 0.72f
        private const val CROP_MARGIN = 0.22f

        /** InsightFace ArcFace 5-point template for 112×112 (src[:,0] += 8). */
        private val ARC_FACE_DST = listOf(
            PointF(38.2946f, 51.6963f),
            PointF(73.5318f, 51.5014f),
            PointF(56.0252f, 71.7366f),
            PointF(41.5493f, 92.3655f),
            PointF(70.7299f, 92.2041f)
        )

        @Volatile
        private var instance: ArcFaceEmbedder? = null

        fun getInstance(context: Context): ArcFaceEmbedder {
            return instance ?: synchronized(this) {
                instance ?: ArcFaceEmbedder(context.applicationContext).also { instance = it }
            }
        }

        fun alignFace(
            bitmap: Bitmap,
            normalizedBox: RectF?,
            meshPoints: List<PointF>,
            outSize: Int
        ): Bitmap? {
            val five = fivePointsFromMesh(meshPoints, bitmap.width, bitmap.height)
            if (five != null) {
                return warpToArcFace(bitmap, orderArcFacePoints(five), outSize)
            }
            return cropFace(bitmap, normalizedBox, outSize)
        }

        fun cropFace(bitmap: Bitmap, normalizedBox: RectF?, outSize: Int): Bitmap? {
            val source = softwareBitmap(bitmap) ?: return null
            if (source.width < 8 || source.height < 8) return null
            val src = if (normalizedBox != null && normalizedBox.width() > 0.05f && normalizedBox.height() > 0.05f) {
                val padX = normalizedBox.width() * CROP_MARGIN
                val padY = normalizedBox.height() * CROP_MARGIN
                val left = ((normalizedBox.left - padX) * source.width).toInt()
                val top = ((normalizedBox.top - padY) * source.height).toInt()
                val right = ((normalizedBox.right + padX) * source.width).toInt()
                val bottom = ((normalizedBox.bottom + padY) * source.height).toInt()
                Rect(
                    left.coerceIn(0, source.width - 1),
                    top.coerceIn(0, source.height - 1),
                    right.coerceIn(1, source.width),
                    bottom.coerceIn(1, source.height)
                )
            } else {
                val side = minOf(source.width, source.height)
                val left = (source.width - side) / 2
                val top = (source.height - side) / 2
                Rect(left, top, left + side, top + side)
            }
            if (src.width() < 8 || src.height() < 8) return null
            val cropped = Bitmap.createBitmap(source, src.left, src.top, src.width(), src.height())
            return Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
        }

        private fun loadOnnx(context: Context): LoadedOnnx? {
            val assetName = pickModelAsset(context) ?: run {
                Log.i(TAG, "No ArcFace ONNX in assets ($MODEL_MBF)")
                return null
            }
            return try {
                val modelFile = copyAssetToFiles(context, assetName)
                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val session = env.createSession(modelFile.absolutePath, options)
                val inName = session.inputNames.first()
                val inInfo = session.inputInfo[inName]?.info as? TensorInfo
                val inShape = inInfo?.shape ?: longArrayOf(1, 3, 112, 112)
                // InsightFace w600k_* is always NCHW RGB 112. Dynamic dims can look like -1/0.
                val nchw = !(inShape.size >= 4 && inShape[3] == 3L && inShape[1] != 3L)
                val size = 112
                val outName = session.outputNames.first()
                val outInfo = session.outputInfo[outName]?.info as? TensorInfo
                val dim = outInfo?.shape?.firstOrNull { it >= 128L }?.toInt() ?: 512
                Log.i(TAG, "Loaded $assetName in=$inName shape=${inShape.contentToString()} out=$outName dim=$dim nchw=$nchw")
                LoadedOnnx(env, session, inName, nchw, size, dim, assetName)
            } catch (t: Throwable) {
                Log.i(TAG, "ONNX ArcFace load failed for $assetName; using crop descriptor", t)
                null
            }
        }

        private fun pickModelAsset(context: Context): String? {
            return try {
                context.assets.open(MODEL_MBF).use { /* exists */ }
                MODEL_MBF
            } catch (_: Exception) {
                null
            }
        }

        private fun copyAssetToFiles(context: Context, assetName: String): File {
            val dir = File(context.filesDir, "onnx_models")
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, assetName)
            val expected = try {
                context.assets.openFd(assetName).use { it.length }
            } catch (_: Exception) {
                -1L
            }
            if (out.exists() && out.length() > 1_000_000L && (expected < 0L || out.length() == expected)) {
                return out
            }
            context.assets.open(assetName).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            return out
        }

        private fun softwareBitmap(bitmap: Bitmap): Bitmap? {
            return try {
                if (bitmap.config == Bitmap.Config.ARGB_8888) {
                    bitmap
                } else {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not copy bitmap to ARGB", t)
                null
            }
        }

        private fun downscaleForDetect(bitmap: Bitmap, maxSide: Int = 640): Bitmap {
            val side = maxOf(bitmap.width, bitmap.height)
            if (side <= maxSide) return bitmap
            val scale = maxSide / side.toFloat()
            val w = (bitmap.width * scale).toInt().coerceAtLeast(8)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(8)
            return Bitmap.createScaledBitmap(bitmap, w, h, true)
        }

        private fun flipHorizontal(bitmap: Bitmap): Bitmap {
            val matrix = Matrix().apply {
                setScale(-1f, 1f)
                postTranslate(bitmap.width.toFloat(), 0f)
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private fun fivePointsFromMlKit(face: Face): List<PointF>? {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
            val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
            val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
            return listOf(leftEye, rightEye, nose, mouthLeft, mouthRight)
        }

        /** ArcFace expects image-left / image-right, not anatomical left/right. */
        private fun orderArcFacePoints(points: List<PointF>): List<PointF> {
            if (points.size != 5) return points
            val eyes = listOf(points[0], points[1]).sortedBy { it.x }
            val mouth = listOf(points[3], points[4]).sortedBy { it.x }
            return listOf(eyes[0], eyes[1], points[2], mouth[0], mouth[1])
        }

        private fun fivePointsFromMesh(mesh: List<PointF>, width: Int, height: Int): List<PointF>? {
            if (mesh.size < 292) return null
            fun px(index: Int): PointF {
                val p = mesh[index]
                return PointF(p.x * width, p.y * height)
            }
            val leftEye = PointF(
                (mesh[33].x + mesh[133].x) * 0.5f * width,
                (mesh[33].y + mesh[133].y) * 0.5f * height
            )
            val rightEye = PointF(
                (mesh[263].x + mesh[362].x) * 0.5f * width,
                (mesh[263].y + mesh[362].y) * 0.5f * height
            )
            return listOf(leftEye, rightEye, px(1), px(61), px(291))
        }

        private fun warpToArcFace(bitmap: Bitmap, srcPoints: List<PointF>, outSize: Int): Bitmap? {
            val source = softwareBitmap(bitmap) ?: return null
            if (srcPoints.size != 5) return cropFace(source, null, outSize)
            val scale = outSize / 112f
            val dst = ARC_FACE_DST.map { PointF(it.x * scale, it.y * scale) }
            val matrix = similarityMatrix(srcPoints, dst) ?: return cropFace(source, null, outSize)
            val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }

        private fun similarityMatrix(src: List<PointF>, dst: List<PointF>): Matrix? {
            val n = minOf(src.size, dst.size)
            if (n < 3) return null
            var srcMx = 0f
            var srcMy = 0f
            var dstMx = 0f
            var dstMy = 0f
            for (i in 0 until n) {
                srcMx += src[i].x
                srcMy += src[i].y
                dstMx += dst[i].x
                dstMy += dst[i].y
            }
            val nf = n.toFloat()
            srcMx /= nf
            srcMy /= nf
            dstMx /= nf
            dstMy /= nf
            var varSrc = 0f
            var a = 0f
            var b = 0f
            for (i in 0 until n) {
                val sx = src[i].x - srcMx
                val sy = src[i].y - srcMy
                val dx = dst[i].x - dstMx
                val dy = dst[i].y - dstMy
                varSrc += sx * sx + sy * sy
                a += sx * dx + sy * dy
                b += sx * dy - sy * dx
            }
            if (varSrc < 1e-6f) return null
            a /= varSrc
            b /= varSrc
            val tx = dstMx - (a * srcMx - b * srcMy)
            val ty = dstMy - (b * srcMx + a * srcMy)
            return Matrix().apply {
                setValues(
                    floatArrayOf(
                        a, -b, tx,
                        b, a, ty,
                        0f, 0f, 1f
                    )
                )
            }
        }

        private fun bitmapToOnnxBuffer(bitmap: Bitmap, nchw: Boolean): java.nio.FloatBuffer {
            val h = bitmap.height
            val w = bitmap.width
            val buffer = ByteBuffer.allocateDirect(1 * 3 * h * w * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            if (nchw) {
                val r = FloatArray(h * w)
                val g = FloatArray(h * w)
                val b = FloatArray(h * w)
                var i = 0
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val c = bitmap.getPixel(x, y)
                        r[i] = (Color.red(c) - 127.5f) / 127.5f
                        g[i] = (Color.green(c) - 127.5f) / 127.5f
                        b[i] = (Color.blue(c) - 127.5f) / 127.5f
                        i++
                    }
                }
                buffer.put(r)
                buffer.put(g)
                buffer.put(b)
            } else {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val c = bitmap.getPixel(x, y)
                        buffer.put((Color.red(c) - 127.5f) / 127.5f)
                        buffer.put((Color.green(c) - 127.5f) / 127.5f)
                        buffer.put((Color.blue(c) - 127.5f) / 127.5f)
                    }
                }
            }
            buffer.rewind()
            return buffer
        }

        private fun extractEmbedding(value: Any?): FloatArray? {
            val flat = flattenFloats(value) ?: return null
            return if (flat.size >= MIN_TEMPLATE_SIZE) flat else null
        }

        private fun flattenFloats(value: Any?): FloatArray? {
            return when (value) {
                is FloatArray -> value
                is Array<*> -> {
                    val parts = value.mapNotNull { flattenFloats(it) }
                    if (parts.isEmpty()) return null
                    val out = FloatArray(parts.sumOf { it.size })
                    var offset = 0
                    for (part in parts) {
                        part.copyInto(out, offset)
                        offset += part.size
                    }
                    out
                }
                else -> null
            }
        }

        private fun l2Normalize(values: FloatArray): FloatArray {
            var sum = 0f
            for (v in values) sum += v * v
            val norm = sqrt(sum).coerceAtLeast(1e-6f)
            val out = FloatArray(values.size)
            for (i in values.indices) out[i] = values[i] / norm
            return out
        }

        private fun uniformLbpBin(code: Int): Int {
            val rotated = (code shl 1) or (code ushr 7)
            val transitions = Integer.bitCount(code xor (rotated and 0xFF))
            return if (transitions <= 2) code % (LBP_BINS - 1) else LBP_BINS - 1
        }

        private fun equalizeInPlace(gray: IntArray, size: Int) {
            val hist = IntArray(256)
            for (v in gray) hist[v.coerceIn(0, 255)]++
            val cdf = IntArray(256)
            var acc = 0
            var cdfMin = 0
            for (i in 0..255) {
                acc += hist[i]
                cdf[i] = acc
                if (cdfMin == 0 && acc > 0) cdfMin = acc
            }
            val total = size * size
            val denom = (total - cdfMin).coerceAtLeast(1).toFloat()
            for (i in gray.indices) {
                val v = gray[i].coerceIn(0, 255)
                gray[i] = (((cdf[v] - cdfMin) / denom) * 255f).toInt().coerceIn(0, 255)
            }
        }
    }
}
