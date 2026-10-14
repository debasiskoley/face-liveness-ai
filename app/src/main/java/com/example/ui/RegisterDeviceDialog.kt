package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.GateDto
import com.example.viewmodel.DeviceRegistrationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterDeviceDialog(
    state: DeviceRegistrationUiState,
    onSelectGate: (Int) -> Unit,
    onRegister: () -> Unit,
    onRetryLoad: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = state.isRegistering

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF0B132B),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        modifier = Modifier.testTag("register_device_dialog"),
        title = { Text(text = "Register Device", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoLine(label = "Device ID", value = state.deviceId.ifBlank { "Unavailable" })
                state.registeredGate?.let {
                    InfoLine(label = "Registered to", value = it.gateName)
                }

                GateDropdown(
                    gates = state.gates,
                    selected = state.selectedGate,
                    isLoading = state.isLoadingGates,
                    enabled = !busy && !state.isLoadingGates && state.gates.isNotEmpty(),
                    onSelect = onSelectGate
                )

                state.successMessage?.let {
                    StatusMessage(text = it, color = Color(0xFF10B981), isError = false)
                }
                state.errorMessage?.let {
                    StatusMessage(text = it, color = Color(0xFFEF4444), isError = true)
                }
            }
        },
        confirmButton = {
            if (state.successMessage != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF030712)
                    )
                ) {
                    Text(text = "Done", fontWeight = FontWeight.Bold)
                }
            } else if (state.gates.isEmpty() && !state.isLoadingGates) {
                Button(
                    onClick = onRetryLoad,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF030712)
                    )
                ) {
                    Text(text = "Retry", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRegister,
                    enabled = state.canRegister,
                    modifier = Modifier.testTag("enroll_device_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF030712)
                    )
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(text = "Enroll Device", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (state.successMessage == null) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(text = "Cancel", color = Color(0xFF94A3B8))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateDropdown(
    gates: List<GateDto>,
    selected: GateDto?,
    isLoading: Boolean,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = when {
                isLoading -> "Loading gates…"
                selected != null -> gateLabel(selected)
                else -> ""
            },
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Gate") },
            placeholder = { Text("Select a gate") },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .testTag("gate_dropdown"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color(0xFF94A3B8),
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF334155),
                disabledBorderColor = Color(0xFF1E293B),
                focusedLabelColor = Color(0xFF00E5FF),
                unfocusedLabelColor = Color(0xFF94A3B8),
                disabledLabelColor = Color(0xFF64748B),
                focusedTrailingIconColor = Color(0xFF00E5FF),
                unfocusedTrailingIconColor = Color(0xFF94A3B8),
                focusedPlaceholderColor = Color(0xFF64748B),
                unfocusedPlaceholderColor = Color(0xFF64748B)
            )
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF111C36)
        ) {
            gates.forEach { gate ->
                DropdownMenuItem(
                    text = { Text(text = gateLabel(gate), color = Color.White) },
                    onClick = {
                        onSelect(gate.gateId)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun StatusMessage(text: String, color: Color, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = color, fontSize = 13.sp)
    }
}

private fun gateLabel(gate: GateDto): String = gate.gateName?.takeIf { it.isNotBlank() } ?: "Gate ${gate.gateId}"
