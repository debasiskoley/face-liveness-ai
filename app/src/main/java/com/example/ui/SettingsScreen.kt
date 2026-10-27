package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchConfig
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    matchConfig: MatchConfig,
    registeredGateName: String? = null,
    faceCacheCount: Int = 0,
    isSyncingFaces: Boolean = false,
    faceCacheStatusText: String? = null,
    onThresholdChange: (Float) -> Unit,
    onMaxSpoofChange: (Float) -> Unit,
    onRefreshFaceCache: () -> Unit = {},
    onRegisterDevice: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppText
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Match Configuration")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MatchSlider(
                        label = "Match threshold (cosine)",
                        value = matchConfig.threshold,
                        onValueChange = onThresholdChange,
                        testTag = "match_threshold_slider"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = AppBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    MatchSlider(
                        label = "Max spoof probability",
                        value = matchConfig.maxSpoofProbability,
                        onValueChange = onMaxSpoofChange,
                        testTag = "max_spoof_slider"
                    )
                }
            }

            SectionTitle("Face cache")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSyncingFaces, onClick = onRefreshFaceCache)
                    .testTag("settings_refresh_face_cache_row"),
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSyncingFaces) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = AppButton
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = AppButton,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Refresh face cache",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppText
                        )
                        Text(
                            text = faceCacheStatusText
                                ?: "$faceCacheCount pose templates stored locally",
                            fontSize = 12.sp,
                            color = AppTextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppTextMuted
                    )
                }
            }

            SectionTitle("Device")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRegisterDevice)
                    .testTag("settings_register_device_row"),
                shape = RoundedCornerShape(16.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AppRegistration,
                        contentDescription = null,
                        tint = AppButton,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Register device",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppText
                        )
                        Text(
                            text = registeredGateName?.let { "Registered to $it" }
                                ?: "Select a gate for this device",
                            fontSize = 12.sp,
                            color = AppTextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppText,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun MatchSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 14.sp, color = AppText)
            Text(
                text = formatDecimal(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppText
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.95f,
            steps = 17,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = AppButton,
                activeTrackColor = AppButton,
                inactiveTrackColor = AppBorder.copy(alpha = 0.4f)
            )
        )
    }
}

private fun formatDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)
