package com.example.ui

import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppError
import com.example.ui.theme.AppSuccess
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.AppWarning
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.remote.EnrollFaceResponse
import com.example.model.EnrollPose
import com.example.model.SpoofRiskLevel
import com.example.viewmodel.EnrollFaceUiState
import com.example.viewmodel.EnrollFaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollFaceScreen(
    mobileNumber: String,
    viewModel: EnrollFaceViewModel,
    onBack: () -> Unit,
    onEnrolled: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("enroll_face_screen"),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Enroll Face",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppText
                        )
                        Text(
                            text = "+91 $mobileNumber · ${uiState.capturedCount}/${uiState.poses.size} poses",
                            fontSize = 11.sp,
                            color = AppText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !uiState.isSubmitting && !uiState.isCapturingPose
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PoseStepRow(
                uiState = uiState,
                onSelect = viewModel::selectPose,
                onRetake = viewModel::retakePose
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppSurface),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraViewfinderView(
                        isFrontCamera = true,
                        isSimulationMode = false,
                        metrics = uiState.metrics,
                        spoofResult = uiState.spoofResult,
                        isLivenessVerified = false,
                        onMediaPipeDetected = viewModel::onMediaPipeDetected,
                        onFaceDetected = viewModel::onFaceDetected
                    )
                } else {
                    CameraPermissionMessage(
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }

            EnrollStatusCard(uiState)

            if (!uiState.allPosesCaptured) {
                Button(
                    onClick = { viewModel.captureCurrentPose() },
                    enabled = hasCameraPermission && uiState.canCapturePose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("capture_pose_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppButton,
                        contentColor = AppText,
                        disabledContainerColor = AppBorder.copy(alpha = 0.35f),
                        disabledContentColor = AppText.copy(alpha = 0.6f)
                    )
                ) {
                    if (uiState.isCapturingPose) {
                        CircularProgressIndicator(
                            color = AppText,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Capturing…", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.Face, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Capture ${uiState.currentPose.title}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.enroll(mobileNumber) },
                    enabled = hasCameraPermission && uiState.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("capture_enroll_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppButton,
                        contentColor = AppText,
                        disabledContainerColor = AppBorder.copy(alpha = 0.35f),
                        disabledContentColor = AppText.copy(alpha = 0.6f)
                    )
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            color = AppText,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Enrolling…", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Submit all poses",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    uiState.result?.let { result ->
        EnrollSuccessDialog(
            mobileNumber = mobileNumber,
            result = result,
            poseCount = uiState.capturedCount,
            onDone = onEnrolled
        )
    }
}

@Composable
private fun PoseStepRow(
    uiState: EnrollFaceUiState,
    onSelect: (Int) -> Unit,
    onRetake: (EnrollPose) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.poses.forEachIndexed { index, pose ->
            val captured = uiState.captured.containsKey(pose)
            val selected = index == uiState.currentPoseIndex
            val borderColor = when {
                captured -> AppSuccess
                selected -> AppButton
                else -> AppBorder
            }
            Surface(
                modifier = Modifier
                    .testTag("pose_chip_${pose.apiValue}")
                    .clickable(enabled = !uiState.isSubmitting && !uiState.isCapturingPose) {
                        onSelect(index)
                    },
                shape = RoundedCornerShape(999.dp),
                color = if (captured) AppSuccess.copy(alpha = 0.15f) else AppSurface,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (captured) "✓ ${pose.title}" else pose.title,
                        color = if (captured) AppSuccess else AppText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (captured && !uiState.isSubmitting && uiState.result == null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { onRetake(pose) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Retake", fontSize = 11.sp, color = AppTextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollStatusCard(uiState: EnrollFaceUiState) {
    val pose = uiState.currentPose
    val error = uiState.errorMessage
    val hint = uiState.hintMessage
    val (message, color) = when {
        error != null -> error to AppError
        uiState.isSubmitting -> "Uploading ${uiState.capturedCount} pose templates…" to AppText
        uiState.isCapturingPose -> "Creating ArcFace template for ${pose.title}…" to AppText
        uiState.allPosesCaptured -> "All poses captured. Tap Submit all poses." to AppSuccess
        hint != null -> hint to AppWarning
        !uiState.metrics.hasFace -> "Position your face inside the frame" to AppTextMuted
        uiState.spoofResult.riskLevel == SpoofRiskLevel.HIGH ->
            (uiState.spoofResult.detectionNotes.firstOrNull() ?: "Possible spoof detected") to AppWarning
        !uiState.poseMatchesCurrent -> pose.instruction to AppWarning
        else -> "Hold still, then tap Capture ${pose.title}" to AppSuccess
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enroll_status_card"),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Step ${uiState.currentPoseIndex + 1}/${uiState.poses.size}: ${pose.title}",
                color = AppText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (error != null) Icons.Default.ErrorOutline else Icons.Default.Face,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = message, color = color, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CameraPermissionMessage(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera permission is needed to capture your face.",
            color = AppText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

@Composable
private fun EnrollSuccessDialog(
    mobileNumber: String,
    result: EnrollFaceResponse,
    poseCount: Int,
    onDone: () -> Unit
) {
    val savedPoses = result.faces?.size ?: poseCount
    AlertDialog(
        onDismissRequest = {},
        containerColor = AppSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AppSuccess,
                modifier = Modifier.size(40.dp)
            )
        },
        title = { Text(text = "Face enrolled", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Mobile: +91 ${result.mobileNumber ?: mobileNumber}", color = AppText)
                result.faceId?.let { Text(text = "Face ID: $it", color = AppText) }
                Text(text = "Poses saved: $savedPoses", color = AppText)
                result.enrolledAt?.let { Text(text = "Enrolled at: $it", color = AppText) }
                result.message?.let { Text(text = it, color = AppTextMuted, fontSize = 13.sp) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Front, left, right, up and down templates are ready for matching.",
                    color = AppTextMuted,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDone,
                modifier = Modifier.testTag("enroll_done_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppButton,
                    contentColor = AppText
                )
            ) {
                Text(text = "Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}
