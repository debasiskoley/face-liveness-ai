package com.example

import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppButton
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.remote.ApiClient
import com.example.ui.EnrollFaceScreen
import com.example.ui.HomeScreen
import com.example.ui.MobileAction
import com.example.ui.MobileNumberDialog
import com.example.ui.RegisterDeviceDialog
import com.example.ui.SettingsScreen
import com.example.ui.SplashScreen
import com.example.ui.VerificationSuccessScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.AppTab
import com.example.viewmodel.DeviceRegistrationViewModel
import com.example.viewmodel.EnrollFaceViewModel
import com.example.viewmodel.FaceLivenessViewModel
import com.example.viewmodel.LoginEvent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FaceLivenessViewModel by viewModels()
    private val enrollViewModel: EnrollFaceViewModel by viewModels()
    private val deviceViewModel: DeviceRegistrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MyApplicationTheme {
                FaceLivenessRoot(
                    viewModel = viewModel,
                    enrollViewModel = enrollViewModel,
                    deviceViewModel = deviceViewModel
                )
            }
        }
    }
}

private enum class AppRoute {
    SPLASH,
    WELCOME,
    ENROLL_FACE,
    DASHBOARD
}

@Composable
fun FaceLivenessRoot(
    viewModel: FaceLivenessViewModel,
    enrollViewModel: EnrollFaceViewModel,
    deviceViewModel: DeviceRegistrationViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val deviceState by deviceViewModel.uiState.collectAsState()
    var route by rememberSaveable { mutableStateOf(AppRoute.SPLASH) }
    var mobileAction by rememberSaveable { mutableStateOf<MobileAction?>(null) }
    var showRegisterDevice by rememberSaveable { mutableStateOf(false) }
    var checkingDevice by remember { mutableStateOf(false) }
    var startingRecognize by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var enrollMobile by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.loginEvents.collect { event ->
            when (event) {
                LoginEvent.Success -> {
                    mobileAction = null
                    startingRecognize = false
                    route = AppRoute.DASHBOARD
                }
                is LoginEvent.Error -> {
                    startingRecognize = false
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    when (route) {
        AppRoute.SPLASH -> SplashScreen(onFinished = { route = AppRoute.WELCOME })

        AppRoute.WELCOME -> {
            LaunchedEffect(Unit) { deviceViewModel.refreshRegistration() }
            WelcomeScreen(
                onLoginWithFace = {
                    if (!checkingDevice && !uiState.isLoggingIn) {
                        checkingDevice = true
                        scope.launch {
                            val registered = deviceViewModel.refreshRegistration()
                            checkingDevice = false
                            if (registered) {
                                mobileAction = MobileAction.LOGIN_WITH_FACE
                            } else {
                                Toast.makeText(context, "Register device before login", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onStartRecognize = {
                    if (!checkingDevice && !uiState.isLoggingIn && !startingRecognize) {
                        startingRecognize = true
                        scope.launch {
                            val registered = deviceViewModel.refreshRegistration()
                            if (registered) {
                                viewModel.startRecognize(
                                    deviceId = deviceState.deviceId,
                                    gateId = deviceState.registeredGate?.gateId
                                )
                            } else {
                                startingRecognize = false
                                Toast.makeText(context, "Register device before login", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                isCheckingDevice = checkingDevice,
                isStartingRecognize = startingRecognize,
                onEnrollFace = { mobileAction = MobileAction.ENROLL_FACE },
                onRegisterDevice = {
                    deviceViewModel.open()
                    showRegisterDevice = true
                },
                registeredGateName = deviceState.registeredGate?.gateName
            )
            if (showRegisterDevice) {
                RegisterDeviceDialog(
                    state = deviceState,
                    onSelectGate = deviceViewModel::selectGate,
                    onRegister = deviceViewModel::register,
                    onRetryLoad = deviceViewModel::loadGates,
                    onDismiss = { showRegisterDevice = false }
                )
            }
            mobileAction?.let { action ->
                MobileNumberDialog(
                    action = action,
                    isLoading = action == MobileAction.LOGIN_WITH_FACE && uiState.isLoggingIn,
                    onDismiss = { mobileAction = null },
                    onConfirm = { mobile ->
                        when (action) {
                            MobileAction.LOGIN_WITH_FACE -> viewModel.loginWithFace(
                                mobileNumber = mobile,
                                deviceId = deviceState.deviceId,
                                gateId = deviceState.registeredGate?.gateId
                            )
                            MobileAction.ENROLL_FACE -> {
                                mobileAction = null
                                enrollViewModel.reset()
                                enrollMobile = mobile
                                route = AppRoute.ENROLL_FACE
                            }
                        }
                    }
                )
            }
        }

        AppRoute.ENROLL_FACE -> {
            val leaveEnroll = {
                enrollViewModel.reset()
                route = AppRoute.WELCOME
            }
            BackHandler(onBack = leaveEnroll)
            EnrollFaceScreen(
                mobileNumber = enrollMobile,
                viewModel = enrollViewModel,
                onBack = leaveEnroll,
                onEnrolled = leaveEnroll
            )
        }

        AppRoute.DASHBOARD -> {
            // The fetched face lives only in memory, so a restored process has nothing to match against.
            if (uiState.enrolledProfiles.isEmpty()) {
                LaunchedEffect(Unit) { route = AppRoute.WELCOME }
            } else {
                FaceLivenessApp(
                    viewModel = viewModel,
                    deviceViewModel = deviceViewModel,
                    onLogout = {
                        viewModel.logout()
                        route = AppRoute.WELCOME
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceLivenessApp(
    viewModel: FaceLivenessViewModel,
    deviceViewModel: DeviceRegistrationViewModel,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val uiDevice by deviceViewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCameraPermission(isGranted)
    }

    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (currentPermission == PackageManager.PERMISSION_GRANTED) {
            viewModel.setCameraPermission(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(enabled = uiState.currentScreen != AppScreen.MAIN_DASHBOARD || uiState.isOfficeInActive) {
        if (uiState.isOfficeInActive) {
            viewModel.cancelOfficeIn()
        } else {
            viewModel.navigateToScreen(AppScreen.MAIN_DASHBOARD)
        }
    }

    when (uiState.currentScreen) {
        AppScreen.VERIFICATION_SUCCESS -> {
            VerificationSuccessScreen(
                capturedBitmap = uiState.verifiedCapturedBitmap ?: uiState.lastCapturedBitmap,
                officeInTimestamp = uiState.officeInTimestamp,
                identityMatch = uiState.identityMatch,
                onNavigateBack = { viewModel.navigateToScreen(AppScreen.MAIN_DASHBOARD) },
                onRestartVerification = { viewModel.restartVerificationFlow() },
                onLogout = onLogout,
                isRecognizeMode = uiState.isRecognizeMode,
                isPostingMatchResult = uiState.isPostingMatchResult,
                matchResultMessage = uiState.matchResultMessage,
                onDismissMatchResult = viewModel::dismissMatchResultMessage,
                onRetryMatchResult = viewModel::retryMatchResult
            )
        }

        AppScreen.MAIN_DASHBOARD -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = AppBackground,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = AppButton,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = AppText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Face Liveness AI",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppText
                                    )
                                    Text(
                                        text = if (uiState.selectedTab == AppTab.HOME) {
                                            "Home · Office In"
                                        } else {
                                            "Settings"
                                        },
                                        fontSize = 10.sp,
                                        color = AppText
                                    )
                                }
                            }
                        },
                        actions = {
                            if (uiState.isOfficeInActive) {
                                IconButton(
                                    onClick = { viewModel.toggleCameraFacing() },
                                    modifier = Modifier.testTag("switch_camera_facing_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = "Switch Camera Facing",
                                        tint = AppText
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AppSurface
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = AppSurface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == AppTab.HOME,
                            onClick = { viewModel.setTab(AppTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppText,
                                selectedTextColor = AppText,
                                indicatorColor = AppButton,
                                unselectedIconColor = AppTextMuted,
                                unselectedTextColor = AppTextMuted
                            ),
                            modifier = Modifier.testTag("tab_home")
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == AppTab.SETTINGS,
                            onClick = { viewModel.setTab(AppTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Settings") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppText,
                                selectedTextColor = AppText,
                                indicatorColor = AppButton,
                                unselectedIconColor = AppTextMuted,
                                unselectedTextColor = AppTextMuted
                            ),
                            modifier = Modifier.testTag("tab_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (uiState.selectedTab) {
                        AppTab.HOME -> {
                            HomeScreen(
                                isOfficeInActive = uiState.isOfficeInActive,
                                isFrontCamera = uiState.isFrontCamera,
                                isSimulationMode = uiState.isSimulationMode,
                                metrics = uiState.metrics,
                                spoofResult = uiState.spoofResult,
                                isLivenessVerified = uiState.isLivenessVerified,
                                isMatchingIdentity = uiState.isMatchingIdentity,
                                challenges = uiState.challenges,
                                currentChallengeIndex = uiState.currentChallengeIndex,
                                challengeStatusText = uiState.challengeStatusText,
                                lastOfficeInTimestamp = uiState.officeInTimestamp,
                                registeredGateName = uiDevice.registeredGate?.gateName,
                                onOfficeInClick = { viewModel.startOfficeIn() },
                                onCancelOfficeIn = { viewModel.cancelOfficeIn() },
                                onRestartChallenges = { viewModel.resetChallenges() },
                                onToggleCameraFacing = { viewModel.toggleCameraFacing() },
                                onMediaPipeDetected = { mpResult, bmp ->
                                    viewModel.onMediaPipeDetected(mpResult, bmp)
                                },
                                onFaceDetected = { face, w, h, bmp ->
                                    viewModel.onFaceDetected(face, w, h, bmp)
                                },
                                onLogout = onLogout,
                                showLogout = uiState.isRecognizeMode
                            )
                        }

                        AppTab.SETTINGS -> {
                            SettingsScreen(
                                matchConfig = uiState.matchConfig,
                            )
                        }
                    }
                }
            }
        }
    }
}
