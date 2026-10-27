package com.example.ui

import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppPrimary
import com.example.ui.theme.AppTextMuted
import kotlinx.coroutines.delay

private const val SPLASH_MIN_DURATION_MS = 900L

@Composable
fun SplashScreen(
    statusText: String? = null,
    prepare: suspend () -> Unit = {},
    onFinished: () -> Unit
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentPrepare by rememberUpdatedState(prepare)
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        currentPrepare()
        val elapsed = System.currentTimeMillis() - started
        val remaining = SPLASH_MIN_DURATION_MS - elapsed
        if (remaining > 0) delay(remaining)
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            AppLogo(size = 96)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Face Liveness AI",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppText
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Secure face attendance",
                fontSize = 13.sp,
                color = AppText
            )
            Spacer(modifier = Modifier.height(36.dp))
            CircularProgressIndicator(
                color = AppText,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            if (!statusText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = AppTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AppLogo(size: Int) {
    Surface(
        shape = CircleShape,
        color = AppButton,
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size((size * 0.55f).dp)
            )
        }
    }
}
