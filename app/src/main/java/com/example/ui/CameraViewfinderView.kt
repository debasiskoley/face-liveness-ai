package com.example.ui

import com.example.ui.theme.AppButton
import com.example.ui.theme.AppError
import com.example.ui.theme.AppSuccess
import com.example.ui.theme.AppWarning
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import kotlin.math.max
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.model.FaceMetrics
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraViewfinder"

@Composable
fun CameraViewfinderView(
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    isSimulationMode: Boolean = false,
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult,
    isLivenessVerified: Boolean,
    onMediaPipeDetected: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceDetected: (Face?, Int, Int, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mediaPipeHelper = remember { MediaPipeFaceLandmarkerHelper(context) }
    val cameraSession = remember { CameraSession() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val latestFrontCamera by rememberUpdatedState(isFrontCamera)
    val latestMediaPipe by rememberUpdatedState(onMediaPipeDetected)
    val latestFace by rememberUpdatedState(onFaceDetected)

    // Tear down helpers only when this composable leaves the tree.
    DisposableEffect(Unit) {
        val faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        cameraSession.faceDetector = FaceDetection.getClient(faceDetectorOptions)

        onDispose {
            cameraSession.analysis?.clearAnalyzer()
            cameraSession.provider?.unbindAll()
            cameraSession.provider = null
            cameraSession.analysis = null
            val detector = cameraSession.faceDetector
            cameraSession.faceDetector = null
            cameraSession.executor.execute {
                detector?.close()
                mediaPipeHelper.close()
            }
            cameraSession.executor.shutdown()
        }
    }

    // Rebind CameraX whenever front/back changes (factory alone only ran once before).
    DisposableEffect(isFrontCamera, isSimulationMode, lifecycleOwner) {
        if (isSimulationMode || cameraSession.executor.isShutdown) {
            onDispose { }
        } else {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cancelled = AtomicBoolean(false)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                if (cancelled.get() || cameraSession.executor.isShutdown) return@addListener
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraSession.provider = cameraProvider
                    cameraSession.analysis?.clearAnalyzer()
                    cameraProvider.unbindAll()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(480, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                    val faceDetector = cameraSession.faceDetector
                        ?: return@addListener

                    cameraSession.analysis = imageAnalysis
                    imageAnalysis.setAnalyzer(cameraSession.executor) { imageProxy ->
                        processCameraFrame(
                            imageProxy = imageProxy,
                            mediaPipeHelper = mediaPipeHelper,
                            faceDetector = faceDetector,
                            isFrontCamera = latestFrontCamera,
                            onMediaPipeResult = latestMediaPipe,
                            onFaceResult = latestFace
                        )
                    }

                    if (!cameraProvider.hasCamera(cameraSelector)) {
                        Log.w(TAG, "Requested camera not available: front=$isFrontCamera")
                        return@addListener
                    }

                    mediaPipeHelper.reset()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    Log.i(TAG, "Camera bound front=$isFrontCamera")
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX bind failed (front=$isFrontCamera)", e)
                }
            }, mainExecutor)

            onDispose {
                cancelled.set(true)
                cameraSession.analysis?.clearAnalyzer()
                cameraSession.analysis = null
                cameraSession.provider?.unbindAll()
            }
        }
    }

    // Sweep scan line animation
    val infiniteTransition = rememberInfiniteTransition(label = "laserScan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(modifier = modifier.fillMaxSize().testTag("camera_viewfinder_container")) {
        if (!isSimulationMode) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().testTag("camera_preview_surface")
            )
        } else {
            SimulatedViewfinderCanvas(metrics = metrics)
        }

        BiometricOverlayCanvas(
            metrics = metrics,
            spoofResult = spoofResult,
            isLivenessVerified = isLivenessVerified,
            scanProgress = scanProgress,
            isFrontCamera = isFrontCamera
        )
    }
}

private class CameraSession {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    var provider: ProcessCameraProvider? = null
    var analysis: ImageAnalysis? = null
    var faceDetector: FaceDetector? = null
}

@Composable
private fun SimulatedViewfinderCanvas(metrics: FaceMetrics) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617),
                        Color(0xFF0B132B)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridStep = 40f
            val gridColor = AppButton.copy(alpha = 0.08f)
            var x = 0f
            while (x < w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += gridStep
            }

            // Central holographic focus ring
            drawCircle(
                color = AppButton.copy(alpha = 0.06f),
                radius = w * 0.38f,
                center = Offset(w * 0.5f, h * 0.46f),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
private fun BiometricOverlayCanvas(
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult,
    isLivenessVerified: Boolean,
    scanProgress: Float,
    isFrontCamera: Boolean
) {
    val primaryColor = when {
        isLivenessVerified -> AppSuccess
        spoofResult.riskLevel == SpoofRiskLevel.HIGH -> AppError
        spoofResult.riskLevel == SpoofRiskLevel.MEDIUM -> AppWarning
        else -> AppButton
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Oval dimensions
        val ovalCenterX = w * 0.5f
        val ovalCenterY = h * 0.46f
        val ovalRadiusX = w * 0.38f
        val ovalRadiusY = h * 0.28f

        // 1. Biometric Alignment Oval
        val ovalPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(ovalCenterX, ovalCenterY),
                    radius = ovalRadiusX
                )
            )
        }

        drawOval(
            color = primaryColor.copy(alpha = 0.85f),
            topLeft = Offset(ovalCenterX - ovalRadiusX, ovalCenterY - ovalRadiusY),
            size = Size(ovalRadiusX * 2, ovalRadiusY * 2),
            style = Stroke(
                width = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f), 0f)
            )
        )

        // 2. Corner Alignment Reticles
        val reticleLen = 36f
        val reticleStroke = 4f
        val reticleColor = primaryColor

        // Top-Left
        val rx1 = ovalCenterX - ovalRadiusX - 12f
        val ry1 = ovalCenterY - ovalRadiusY - 12f
        drawLine(reticleColor, Offset(rx1, ry1), Offset(rx1 + reticleLen, ry1), strokeWidth = reticleStroke)
        drawLine(reticleColor, Offset(rx1, ry1), Offset(rx1, ry1 + reticleLen), strokeWidth = reticleStroke)

        // Top-Right
        val rx2 = ovalCenterX + ovalRadiusX + 12f
        val ry2 = ovalCenterY - ovalRadiusY - 12f
        drawLine(reticleColor, Offset(rx2, ry2), Offset(rx2 - reticleLen, ry2), strokeWidth = reticleStroke)
        drawLine(reticleColor, Offset(rx2, ry2), Offset(rx2, ry2 + reticleLen), strokeWidth = reticleStroke)

        // Bottom-Left
        val rx3 = ovalCenterX - ovalRadiusX - 12f
        val ry3 = ovalCenterY + ovalRadiusY + 12f
        drawLine(reticleColor, Offset(rx3, ry3), Offset(rx3 + reticleLen, ry3), strokeWidth = reticleStroke)
        drawLine(reticleColor, Offset(rx3, ry3), Offset(rx3, ry3 - reticleLen), strokeWidth = reticleStroke)

        // Bottom-Right
        val rx4 = ovalCenterX + ovalRadiusX + 12f
        val ry4 = ovalCenterY + ovalRadiusY + 12f
        drawLine(reticleColor, Offset(rx4, ry4), Offset(rx4 - reticleLen, ry4), strokeWidth = reticleStroke)
        drawLine(reticleColor, Offset(rx4, ry4), Offset(rx4, ry4 - reticleLen), strokeWidth = reticleStroke)

        // 3. Dynamic Laser Sweep Line
        if (metrics.hasFace && !isLivenessVerified) {
            val laserY = (ovalCenterY - ovalRadiusY) + (ovalRadiusY * 2f * scanProgress)
            val dxFraction = 1f - kotlin.math.abs(scanProgress - 0.5f) * 2f
            val curRadiusX = ovalRadiusX * kotlin.math.sqrt(kotlin.math.max(0.01f, dxFraction))
            val laserX1 = ovalCenterX - curRadiusX
            val laserX2 = ovalCenterX + curRadiusX

            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.05f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.05f)
                    )
                ),
                start = Offset(laserX1, laserY),
                end = Offset(laserX2, laserY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // 4. MediaPipe mesh only during MiniFASNet PAD. Once live, clear mask for blink.
        val showMeshMask = metrics.hasFace &&
            !spoofResult.isLive &&
            !isLivenessVerified &&
            metrics.meshPoints.isNotEmpty()
        if (showMeshMask) {
            fun mapPoint(normX: Float, normY: Float): Offset {
                val px = if (isFrontCamera) (1f - normX) * w else normX * w
                val py = normY * h
                return Offset(px, py)
            }

            val meshStroke = primaryColor.copy(alpha = 0.35f)
            val mappedPoints = ArrayList<Offset>(metrics.meshPoints.size)

            for (p in metrics.meshPoints) {
                mappedPoints.add(mapPoint(p.x, p.y))
            }

            val step = if (mappedPoints.size > 200) 2 else 1
            for (i in 0 until (mappedPoints.size - 1) step step) {
                val p1 = mappedPoints[i]
                val p2 = mappedPoints[i + 1]
                drawLine(meshStroke, p1, p2, strokeWidth = 1f)
            }

            if (mappedPoints.size >= 36) {
                for (i in 0 until 35) {
                    drawLine(meshStroke, mappedPoints[i], mappedPoints[i + 1], strokeWidth = 1.2f)
                }
                drawLine(meshStroke, mappedPoints[35], mappedPoints[0], strokeWidth = 1.2f)
            }

            val nodeColor = primaryColor.copy(alpha = 0.85f)
            for (p in mappedPoints) {
                drawCircle(nodeColor, radius = 2.2f, center = p)
            }

            val lm = metrics.landmarks
            val keyNodes = listOfNotNull(
                lm.leftEye?.let { mapPoint(it.x, it.y) },
                lm.rightEye?.let { mapPoint(it.x, it.y) },
                lm.noseBase?.let { mapPoint(it.x, it.y) },
                lm.leftMouth?.let { mapPoint(it.x, it.y) },
                lm.rightMouth?.let { mapPoint(it.x, it.y) },
                lm.mouthBottom?.let { mapPoint(it.x, it.y) },
                lm.leftCheek?.let { mapPoint(it.x, it.y) },
                lm.rightCheek?.let { mapPoint(it.x, it.y) }
            )
            for (p in keyNodes) {
                drawCircle(Color.White, radius = 4.5f, center = p)
                drawCircle(primaryColor, radius = 2.5f, center = p)
            }
        } else if (metrics.hasFace && spoofResult.isLive) {
            // Light eye anchors only (no full mesh) for MediaPipe blink guidance.
            fun mapPoint(normX: Float, normY: Float): Offset {
                val px = if (isFrontCamera) (1f - normX) * w else normX * w
                val py = normY * h
                return Offset(px, py)
            }
            val lm = metrics.landmarks
            listOfNotNull(
                lm.leftEye?.let { mapPoint(it.x, it.y) },
                lm.rightEye?.let { mapPoint(it.x, it.y) }
            ).forEach { p ->
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 5f, center = p)
                drawCircle(primaryColor, radius = 2.5f, center = p)
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processCameraFrame(
    imageProxy: ImageProxy,
    mediaPipeHelper: MediaPipeFaceLandmarkerHelper,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    isFrontCamera: Boolean,
    onMediaPipeResult: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceResult: (Face?, Int, Int, Bitmap?) -> Unit
) {
    var bmp: Bitmap? = null
    try {
        val rawBmp = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val longest = max(rawBmp.width, rawBmp.height).toFloat().coerceAtLeast(1f)
        val scale = (ANALYSIS_MAX_SIDE / longest).coerceAtMost(1f)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        bmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
        if (rawBmp != bmp && !rawBmp.isRecycled) {
            rawBmp.recycle()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error converting imageProxy to Bitmap", e)
    }

    if (bmp != null && mediaPipeHelper.isInitialized) {
        try {
            val mpResult = mediaPipeHelper.processBitmap(bmp, isFrontCamera)
            onMediaPipeResult(mpResult, if (mpResult.hasFace) bmp else null)
            imageProxy.close()
            return
        } catch (e: Exception) {
            Log.w(TAG, "MediaPipe run failed, falling back to ML Kit", e)
        }
    }

    // Fallback detection via ML Kit (rare — only when MediaPipe is unavailable).
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

    faceDetector.process(inputImage)
        .addOnSuccessListener { faces ->
            val firstFace = faces.firstOrNull()
            onFaceResult(firstFace, inputImage.width, inputImage.height, bmp)
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Face detection error", e)
            onFaceResult(null, 0, 0, null)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private const val ANALYSIS_MAX_SIDE = 480f
