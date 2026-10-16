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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.remote.EnrollFaceResponse
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
                            text = "+91 $mobileNumber",
                            fontSize = 11.sp,
                            color = AppText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.isSubmitting) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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

            Button(
                onClick = { viewModel.enroll(mobileNumber) },
                enabled = hasCameraPermission && uiState.canCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
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
                    Icon(imageVector = Icons.Default.Face, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Capture & Enroll", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    uiState.result?.let { result ->
        EnrollSuccessDialog(
            mobileNumber = mobileNumber,
            result = result,
            onDone = onEnrolled
        )
    }
}

@Composable
private fun EnrollStatusCard(uiState: EnrollFaceUiState) {
    val error = uiState.errorMessage
    val (message, color) = when {
        error != null -> error to AppError
        uiState.isSubmitting -> "Creating face template and sending to server…" to AppText
        !uiState.metrics.hasFace -> "Position your face inside the frame" to AppTextMuted
        uiState.spoofResult.riskLevel == SpoofRiskLevel.HIGH ->
            (uiState.spoofResult.detectionNotes.firstOrNull() ?: "Possible spoof detected") to AppWarning
        else -> "Face detected. Hold still and tap Capture & Enroll" to AppSuccess
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enroll_status_card"),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    onDone: () -> Unit
) {
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
                result.enrolledAt?.let { Text(text = "Enrolled at: $it", color = AppText) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You can now use Login with Face.",
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
