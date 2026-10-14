package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchConfig
import java.util.Locale

@Composable
fun SettingsScreen(
    employeeName: String,
    employeeId: String,
    mobileNumber: String,
    matchConfig: MatchConfig,
    serverUrl: String,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "Account") {
            if (employeeName.isNotBlank()) {
                SettingsRow(label = "Name", value = employeeName)
                SettingsDivider()
            }
            if (employeeId.isNotBlank()) {
                SettingsRow(label = "Employee ID", value = employeeId)
                SettingsDivider()
            }
            SettingsRow(
                label = "Mobile number",
                value = if (mobileNumber.isBlank()) "Not signed in" else "+91 $mobileNumber"
            )
        }

        SettingsSection(title = "Match Config") {
            SettingsRow(label = "Match threshold (cosine)", value = formatDecimal(matchConfig.threshold))
            SettingsDivider()
            SettingsRow(
                label = "Max spoof probability",
                value = formatDecimal(matchConfig.maxSpoofProbability)
            )
            SettingsDivider()
            SettingsRow(
                label = "Required challenges",
                value = matchConfig.requiredChallenges.joinToString(", ") { it.name }
            )
            SettingsDivider()
            SettingsRow(label = "Embedding model", value = matchConfig.embeddingModel)
            SettingsDivider()
            SettingsRow(label = "Embedding size", value = "${matchConfig.embeddingDim}-D")
            SettingsDivider()
            SettingsRow(label = "Anti-spoof model", value = matchConfig.padModel)
        }

        SettingsSection(title = "Server") {
            SettingsRow(label = "API base URL", value = serverUrl)
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("logout_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Logout", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(Locale.ROOT),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E5FF),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B132B)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
}

private fun formatDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)
