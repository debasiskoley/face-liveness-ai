package com.example.ui

import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onLoginWithFace: () -> Unit,
    onEnrollFace: () -> Unit,
    onRegisterDevice: () -> Unit = {},
    registeredGateName: String? = null,
    isCheckingDevice: Boolean = false
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("welcome_screen"),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (registeredGateName != null) {
                        Text(
                            text = registeredGateName,
                            fontSize = 13.sp,
                            color = AppText
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = onRegisterDevice,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(52.dp)
                            .border(1.dp, AppBorder, CircleShape)
                            .testTag("register_device_button"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AppButton,
                            contentColor = AppText
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AppRegistration,
                            contentDescription = "Register device",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { innerPadding ->
        WelcomeContent(
            modifier = Modifier.padding(innerPadding),
            onLoginWithFace = onLoginWithFace,
            onEnrollFace = onEnrollFace,
            isCheckingDevice = isCheckingDevice
        )
    }
}

@Composable
private fun WelcomeContent(
    modifier: Modifier,
    onLoginWithFace: () -> Unit,
    onEnrollFace: () -> Unit,
    isCheckingDevice: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
                color = AppText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mark your attendance with a live face check",
                fontSize = 14.sp,
                color = AppText,
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
                    containerColor = AppButton,
                    contentColor = AppText
                )
            ) {
                if (isCheckingDevice) {
                    CircularProgressIndicator(
                        color = AppText,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Default.Face, contentDescription = null)
                }
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
                border = BorderStroke(1.dp, AppBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppText)
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
        containerColor = AppSurface,
        titleContentColor = AppText,
        textContentColor = AppText,
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
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppButton,
                        unfocusedBorderColor = AppBorder,
                        focusedLabelColor = AppText,
                        unfocusedLabelColor = AppTextMuted,
                        focusedPrefixColor = AppTextMuted,
                        unfocusedPrefixColor = AppTextMuted,
                        disabledTextColor = AppTextMuted,
                        disabledBorderColor = AppBorder,
                        disabledLabelColor = AppTextMuted,
                        disabledPrefixColor = AppTextMuted,
                        cursorColor = AppButton
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
                    containerColor = AppButton,
                    contentColor = AppText
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = AppText,
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
                Text(text = "Cancel", color = AppTextMuted)
            }
        }
    )
}
