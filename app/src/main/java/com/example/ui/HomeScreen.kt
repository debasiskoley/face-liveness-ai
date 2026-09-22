package com.example.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.mediapipe.MediaPipeFaceLandmarkerHelper
import com.example.model.ChallengeStep
import com.example.model.FaceMetrics
import com.example.model.SpoofDetectionResult
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
    challenges: List<ChallengeStep>,
    currentChallengeIndex: Int,
    challengeStatusText: String,
    lastOfficeInTimestamp: Long?,
    onOfficeInClick: () -> Unit,
    onCancelOfficeIn: () -> Unit,
    onRestartChallenges: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onMediaPipeDetected: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceDetected: (Face?, Int, Int, Bitmap?) -> Unit
) {
    if (isOfficeInActive) {
        OfficeInLivenessFragment(
            isFrontCamera = isFrontCamera,
            isSimulationMode = isSimulationMode,
            metrics = metrics,
            spoofResult = spoofResult,
            isLivenessVerified = isLivenessVerified,
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
            onOfficeInClick = onOfficeInClick
        )
    }
}

@Composable
private fun OfficeInIdleFragment(
    lastOfficeInTimestamp: Long?,
    onOfficeInClick: () -> Unit
) {
    val todayLabel = SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault()).format(Date())
    val lastCheckIn = lastOfficeInTimestamp?.let {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .padding(20.dp)
            .testTag("home_fragment"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Home",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Office Attendance",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = todayLabel,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF00E5FF).copy(alpha = 0.7f), Color(0xFF10B981).copy(alpha = 0.4f))
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
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Mark your attendance",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Office In: MiniFASNet anti-spoof, then MediaPipe center face and blink (mesh clears after live).",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepChip(label = "1. Center")
                    StepChip(label = "2. Blink")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onOfficeInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("office_in_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = Color(0xFF030712),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Office In",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF030712)
                    )
                }

                if (lastCheckIn != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Last Office In · $lastCheckIn",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        Text(
            text = "Camera opens only after you tap Office In.",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF132037)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0)
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
    challenges: List<ChallengeStep>,
    currentChallengeIndex: Int,
    challengeStatusText: String,
    onCancelOfficeIn: () -> Unit,
    onRestartChallenges: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onMediaPipeDetected: (MediaPipeFaceLandmarkerHelper.MediaPipeAnalysisResult, Bitmap?) -> Unit,
    onFaceDetected: (Face?, Int, Int, Bitmap?) -> Unit
) {
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
                color = Color(0xCC0B132B)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Office In · Liveness",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            IconButton(
                onClick = onCancelOfficeIn,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xCC0B132B))
                    .border(1.dp, Color(0xFF334155), CircleShape)
                    .testTag("cancel_office_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Office In",
                    tint = Color.White
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
