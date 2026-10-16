package com.example.ui

import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppError
import com.example.ui.theme.AppSuccess
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.IdentityMatchResult
import com.example.viewmodel.MatchResultMessage
import com.example.viewmodel.MatchResultStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSuccessScreen(
    capturedBitmap: Bitmap?,
    officeInTimestamp: Long?,
    identityMatch: IdentityMatchResult = IdentityMatchResult(),
    onNavigateBack: () -> Unit,
    onRestartVerification: () -> Unit,
    onLogout: () -> Unit = {},
    isRecognizeMode: Boolean = false,
    isPostingMatchResult: Boolean = false,
    matchResultMessage: MatchResultMessage? = null,
    onDismissMatchResult: () -> Unit = {},
    onRetryMatchResult: () -> Unit = {}
) {
    var redirectAfterOk by remember { mutableStateOf(false) }
    LaunchedEffect(redirectAfterOk) {
        if (redirectAfterOk) {
            delay(5_000)
            onNavigateBack()
        }
    }

    matchResultMessage?.let {
        MatchResultDialog(
            message = it,
            onDismiss = {
                onDismissMatchResult()
                if (isRecognizeMode) redirectAfterOk = true
            },
            onRetry = onRetryMatchResult
        )
    }

    val formattedTime = SimpleDateFormat("MMM dd, yyyy · hh:mm:ss a", Locale.getDefault())
        .format(Date(officeInTimestamp ?: System.currentTimeMillis()))
    val matched = identityMatch.isMatch
    val accent = if (matched) AppSuccess else AppError

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (matched) "Office In Successful" else "Office In · No Match",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppText
                        )
                        Text(
                            text = if (matched) {
                                "Liveness passed · Identity matched"
                            } else if (identityMatch.statusLabel.equals("Not enrolled yet", ignoreCase = true)) {
                                "Liveness passed · Not enrolled yet"
                            } else {
                                "Liveness passed · Face not enrolled"
                            },
                            fontSize = 11.sp,
                            color = AppText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("success_screen_back_button")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("verification_success_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, accent, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f))
                            .border(2.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (matched) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "Result",
                            tint = accent,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            matched -> "FACE MATCHED"
                            identityMatch.statusLabel.equals("Not enrolled yet", ignoreCase = true) ->
                                "NOT ENROLLED YET"
                            else -> "FACE NOT MATCHED"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = AppText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (matched) {
                            "${identityMatch.statusLabel} after the liveness check."
                        } else if (identityMatch.statusLabel.equals("Not enrolled yet", ignoreCase = true)) {
                            "This face is not in the enrolled employee list."
                        } else {
                            "Liveness passed. ${identityMatch.statusLabel}."
                        },
                        fontSize = 12.sp,
                        color = accent,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accent
                    ) {
                        Text(
                            text = formattedTime,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppSurface
                        )
                    }

                    if (isPostingMatchResult) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("match_result_posting")
                        ) {
                            CircularProgressIndicator(
                                color = AppText,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Sending result to server…", fontSize = 12.sp, color = AppTextMuted)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppBorder, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        val photo = identityMatch.profile?.photoUri
                        if (!photo.isNullOrBlank()) {
                            AsyncImage(
                                model = photo,
                                contentDescription = "Enrolled profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = AppTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "IDENTITY TEMPLATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AppText
                        )
                        Text(
                            text = identityMatch.profile?.name ?: "No matching enrolled person",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppText
                        )
                        Text(
                            text = "Cosine ${"%.3f".format(identityMatch.cosineSimilarity)} · ${identityMatch.matchPercentage}%",
                            fontSize = 12.sp,
                            color = AppTextMuted
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIVE CAPTURE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = AppText,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppSurface)
                            .border(2.dp, AppSuccess, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayBitmap = remember(capturedBitmap) {
                            capturedBitmap?.let { src ->
                                val matrix = Matrix().apply { postRotate(0f) }
                                Bitmap.createBitmap(
                                    src,
                                    0,
                                    0,
                                    src.width,
                                    src.height,
                                    matrix,
                                    true
                                )
                            }
                        }
                        if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = "Office In capture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AppSuccess,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onRestartVerification,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .testTag("restart_verification_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AppButton),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    tint = AppText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Office In Again",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppText
                )
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .testTag("return_to_dashboard_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = AppText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Home",
                    fontSize = 14.sp,
                    color = AppText
                )
            }

            if (!isRecognizeMode) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("result_logout_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppError.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Logout", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MatchResultDialog(
    message: MatchResultMessage,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val (icon, tint) = when (message.status) {
        MatchResultStatus.RECORDED -> Icons.Default.CheckCircle to AppSuccess
        MatchResultStatus.NOT_RECORDED -> Icons.Default.Info to AppError
        MatchResultStatus.FAILED -> Icons.Default.ErrorOutline to AppError
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSurface,
        modifier = Modifier.testTag("match_result_dialog"),
        icon = { Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp)) },
        title = { Text(text = message.title, color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = message.message, color = AppText)
                message.details.forEach { Text(text = it, color = AppTextMuted, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppButton, contentColor = AppText),
                modifier = Modifier.testTag("match_result_ok_button")
            ) {
                Text(text = "OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (message.status == MatchResultStatus.FAILED) {
            {
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = AppText)) {
                    Text(text = "Retry")
                }
            }
        } else {
            null
        }
    )
}
