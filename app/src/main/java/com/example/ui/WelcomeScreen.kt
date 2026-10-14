package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MobileAction(val title: String, val confirmLabel: String) {
    LOGIN_WITH_FACE("Login with Face", "Continue"),
    ENROLL_FACE("Enroll Face", "Next")
}

private val MOBILE_NUMBER_REGEX = Regex("^[6-9]\\d{9}$")
private const val MOBILE_NUMBER_LENGTH = 10

fun isValidMobileNumber(value: String): Boolean = MOBILE_NUMBER_REGEX.matches(value)

@Composable
fun WelcomeScreen(
    onLoginWithFace: () -> Unit,
    onEnrollFace: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .systemBarsPadding()
            .padding(24.dp)
            .testTag("welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(size = 88)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Face Liveness AI",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mark your attendance with a live face check",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onLoginWithFace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_with_face_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF030712)
                )
            ) {
                Icon(imageVector = Icons.Default.Face, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(text = "Login with Face", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onEnrollFace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("enroll_face_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(text = "Enroll Face", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MobileNumberDialog(
    action: MobileAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean = false
) {
    var mobile by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = {
        if (!isLoading) {
            if (isValidMobileNumber(mobile)) onConfirm(mobile) else showError = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFF0B132B),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = { Text(text = action.title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "Enter your registered mobile number", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { input ->
                        mobile = input.filter(Char::isDigit).take(MOBILE_NUMBER_LENGTH)
                        showError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("mobile_number_input"),
                    label = { Text("Mobile number") },
                    prefix = { Text("+91 ") },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = showError,
                    supportingText = {
                        if (showError) Text("Enter a valid 10-digit mobile number")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        focusedPrefixColor = Color(0xFF94A3B8),
                        unfocusedPrefixColor = Color(0xFF94A3B8),
                        disabledTextColor = Color(0xFF94A3B8),
                        disabledBorderColor = Color(0xFF334155),
                        disabledLabelColor = Color(0xFF64748B),
                        disabledPrefixColor = Color(0xFF64748B),
                        cursorColor = Color(0xFF00E5FF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                enabled = mobile.length == MOBILE_NUMBER_LENGTH && !isLoading,
                modifier = Modifier.testTag("mobile_number_confirm"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF030712)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(text = action.confirmLabel, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(text = "Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
