package com.example.ui

import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppSuccess
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.model.ChallengeStep
import com.example.model.FaceMetrics
import com.example.model.SpoofDetectionResult
import com.example.ui.theme.AppError
import com.example.ui.theme.AppPrimary
import com.google.mlkit.vision.face.Face
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    isOfficeInActive: Boolean,
    isFrontCamera: Boolean,
    isSimulationMode: Boolean,
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult,
    isLivenessVerified: Boolean,
    isMatchingIdentity: Boolean = false,
    challenges: List<ChallengeStep>,
    currentChallengeIndex: Int,
    challengeStatusText: String,
    lastOfficeInTimestamp: Long?,
    registeredGateName: String? = null,
    onOfficeInClick: () -> Unit,
    onCancelOfficeIn: () -> Unit,
    onRestartChallenges: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onMediaPipeDetected: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceDetected: (Face?, Int, Int, Bitmap?) -> Unit,
    onLogout: () -> Unit,
) {
    if (isOfficeInActive) {
        OfficeInLivenessFragment(
            isFrontCamera = isFrontCamera,
            isSimulationMode = isSimulationMode,
            metrics = metrics,
            spoofResult = spoofResult,
            isLivenessVerified = isLivenessVerified,
            isMatchingIdentity = isMatchingIdentity,
            challenges = challenges,
            currentChallengeIndex = currentChallengeIndex,
            challengeStatusText = challengeStatusText,
            onCancelOfficeIn = onCancelOfficeIn,
            onRestartChallenges = onRestartChallenges,
            onToggleCameraFacing = onToggleCameraFacing,
            onMediaPipeDetected = onMediaPipeDetected,
            onFaceDetected = onFaceDetected
        )
    } else {
        OfficeInIdleFragment(
            lastOfficeInTimestamp = lastOfficeInTimestamp,
            registeredGateName = registeredGateName,
            onOfficeInClick = onOfficeInClick,
            onLogout = onLogout,
        )
    }
}

@Composable
private fun OfficeInIdleFragment(
    lastOfficeInTimestamp: Long?,
    registeredGateName: String? = null,
    onOfficeInClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val todayLabel = SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault()).format(Date())
    val lastCheckIn = lastOfficeInTimestamp?.let {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(20.dp)
            .testTag("home_fragment"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {

            if (registeredGateName != null) {
                Text(
                    text = "Assembly point at $registeredGateName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = AppText
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mark Presence",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppText
            )
            Text(
                text = todayLabel,
                fontSize = 14.sp,
                color = AppText
            )
        }

        Column {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(AppButton.copy(alpha = 0.7f), AppSuccess.copy(alpha = 0.4f))
                    ),
                    width = 1.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape,
                        color = AppButton.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                tint = AppButton,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Mark your Presence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Register Presence: Make sure to center your face and Blink Eye properly",
                        fontSize = 13.sp,
                        color = AppTextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onOfficeInClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("office_in_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AppButton),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Record My Presence",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary
                        )
                    }
                }
            }


            TextButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .testTag("back_button"),
//                shape = RoundedCornerShape(14.dp),
//                border = BorderStroke(1.dp, AppError.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppError)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Back to Login", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
//
//        Text(
//            text = "Camera opens only after you tap Record My Presence.",
//            fontSize = 12.sp,
//            color = AppText,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.fillMaxWidth()
//        )
    }
}

@Composable
private fun StepChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppText
        )
    }
}

@Composable
private fun OfficeInLivenessFragment(
    isFrontCamera: Boolean,
    isSimulationMode: Boolean,
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult,
    isLivenessVerified: Boolean,
    isMatchingIdentity: Boolean,
    challenges: List<ChallengeStep>,
    currentChallengeIndex: Int,
    challengeStatusText: String,
    onCancelOfficeIn: () -> Unit,
    onRestartChallenges: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onMediaPipeDetected: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceDetected: (Face?, Int, Int, Bitmap?) -> Unit
) {
    if (isMatchingIdentity) {
        MatchingWaitDialog()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("office_in_liveness_fragment")
    ) {
        CameraViewfinderView(
            isFrontCamera = isFrontCamera,
            isSimulationMode = isSimulationMode,
            metrics = metrics,
            spoofResult = spoofResult,
            isLivenessVerified = isLivenessVerified,
            onMediaPipeDetected = onMediaPipeDetected,
            onFaceDetected = onFaceDetected
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AppSurface.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = AppButton,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Presence Record · Liveness",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppText
                    )
                }
            }

            IconButton(
                onClick = onCancelOfficeIn,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppSurface.copy(alpha = 0.85f))
                    .border(1.dp, AppBorder, CircleShape)
                    .testTag("cancel_office_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel record presence",
                    tint = AppText
                )
            }
        }

        LivenessChallengeCard(
            modifier = Modifier.align(Alignment.BottomCenter),
            challenges = challenges,
            currentStepIndex = currentChallengeIndex,
            isLivenessVerified = isLivenessVerified,
            statusText = challengeStatusText,
            metrics = metrics,
            spoofResult = spoofResult,
            onRestartChallenges = onRestartChallenges,
            onToggleCameraFacing = onToggleCameraFacing
        )
    }
}

@Composable
private fun MatchingWaitDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = AppSurface,
        modifier = Modifier
            .widthIn(min = 220.dp)
            .testTag("matching_wait_dialog"),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = AppButton,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Please wait ..",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppText,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {}
    )
}
