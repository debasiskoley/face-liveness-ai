package com.example.ui

import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppError
import com.example.ui.theme.AppSuccess
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.AppWarning
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChallengeStep
import com.example.model.FaceMetrics
import com.example.model.SpoofDetectionResult
import com.example.model.SpoofRiskLevel

@Composable
fun LivenessChallengeCard(
    modifier: Modifier = Modifier,
    challenges: List<ChallengeStep>,
    currentStepIndex: Int,
    isLivenessVerified: Boolean,
    statusText: String,
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult,
    onRestartChallenges: () -> Unit,
    onToggleCameraFacing: () -> Unit
) {
    val currentStep = challenges.getOrNull(currentStepIndex)
    val isSpoofAlert = spoofResult.riskLevel == SpoofRiskLevel.HIGH

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isSpoofAlert -> AppError
            isLivenessVerified -> AppSuccess
            metrics.hasFace -> AppButton
            else -> AppBorder
        },
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("liveness_challenge_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppSurface.copy(alpha = 0.94f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(cardBorderColor.copy(alpha = 0.8f), cardBorderColor.copy(alpha = 0.3f))
            ),
            width = 1.5.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Status Title & Quick Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cardBorderColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isSpoofAlert && spoofResult.isScreenReplayDetected -> "SCREEN / VIDEO SPOOF"
                            isSpoofAlert -> "SPOOF ATTACK DETECTED"
                            isLivenessVerified -> "LIVENESS VERIFIED"
                            metrics.hasFace -> "ACTIVE LIVENESS CHALLENGE"
                            else -> "ALIGN FACE IN OVAL"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = cardBorderColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLivenessVerified) "PASSED" else "STEP ${currentStepIndex + 1} OF ${challenges.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleCameraFacing,
                        modifier = Modifier.size(32.dp).testTag("toggle_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = AppText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onRestartChallenges,
                        modifier = Modifier.size(32.dp).testTag("restart_challenge_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Challenge",
                            tint = AppText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Challenge Action Prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon avatar
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBorderColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(cardBorderColor, cardBorderColor.copy(alpha = 0.3f))),
                        width = 1.dp
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLivenessVerified) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AppSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (isSpoofAlert) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AppError,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = currentStep?.type?.icon ?: "🎯",
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSpoofAlert) {
                            if (spoofResult.isScreenReplayDetected) {
                                "Warning: Face on a phone/TV screen or video replay"
                            } else {
                                "Warning: Potential presentation attack detected"
                            }
                        } else if (isLivenessVerified) {
                            "Office In liveness confirmed"
                        } else {
                            currentStep?.type?.title ?: "Face Verification"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppText
                    )
                    Text(
                        text = if (isSpoofAlert) {
                            spoofResult.detectionNotes.firstOrNull() ?: "Presentation attack detected."
                        } else {
                            statusText
                        },
                        fontSize = 12.sp,
                        color = AppTextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step Progress Bar
            val animatedProgress by animateFloatAsState(
                targetValue = if (isLivenessVerified) 1f else currentStep?.progress ?: 0f,
                label = "stepProgress"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = cardBorderColor,
                trackColor = AppBorder
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Step Dots Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                challenges.forEachIndexed { index, step ->
                    val isDone = step.isCompleted || (isLivenessVerified)
                    val isCurrent = index == currentStepIndex && !isLivenessVerified

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> AppSuccess
                                        isCurrent -> AppButton
                                        else -> AppBorder
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AppSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) AppText else AppSurface
                                )
                            }
                        }

                        if (index < challenges.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(
                                        if (isDone) AppSuccess.copy(alpha = 0.6f) else AppBorder
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Sensor Telemetry HUD Bar
            TelemetryHudPills(metrics = metrics, spoofResult = spoofResult)

            if (isLivenessVerified) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSuccess
                ) {
                    Text(
                        text = "Recording Office In…",
                        modifier = Modifier.padding(vertical = 14.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryHudPills(
    metrics: FaceMetrics,
    spoofResult: SpoofDetectionResult
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Head Pose Yaw Pill
        HudChip(
            label = "YAW",
            value = "${metrics.headEulerYaw.toInt()}°",
            highlight = kotlin.math.abs(metrics.headEulerYaw) > 12f
        )

        // Eyes Probability Pill
        HudChip(
            label = "EYES",
            value = "${((metrics.leftEyeOpenProb + metrics.rightEyeOpenProb) * 50).toInt()}%",
            highlight = (metrics.leftEyeOpenProb < 0.3f && metrics.rightEyeOpenProb < 0.3f)
        )

        // Spoof Risk Pill
        val riskColor = when (spoofResult.riskLevel) {
            SpoofRiskLevel.LOW -> AppSuccess
            SpoofRiskLevel.MEDIUM -> AppWarning
            SpoofRiskLevel.HIGH -> AppError
        }
        val riskText = when {
            spoofResult.isScreenReplayDetected -> "SCREEN"
            spoofResult.riskLevel == SpoofRiskLevel.LOW -> "LIVE"
            spoofResult.riskLevel == SpoofRiskLevel.MEDIUM -> "CHECK"
            else -> "SPOOF"
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = riskColor.copy(alpha = 0.18f),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(riskColor, riskColor.copy(alpha = 0.4f))),
                width = 1.dp
            ),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(riskColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = riskText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = riskColor
                )
            }
        }
    }
}

@Composable
private fun HudChip(
    label: String,
    value: String,
    highlight: Boolean
) {
    val bg = if (highlight) AppButton.copy(alpha = 0.2f) else AppBorder.copy(alpha = 0.2f)
    val textColor = AppText

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextMuted
            )
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
        }
    }
}
