package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.HomeScreen
import com.example.ui.ProfilesScreen
import com.example.ui.VerificationSuccessScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.AppTab
import com.example.viewmodel.FaceLivenessViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FaceLivenessViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                FaceLivenessApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceLivenessApp(viewModel: FaceLivenessViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

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
                onRestartVerification = { viewModel.restartVerificationFlow() }
            )
        }

        AppScreen.MAIN_DASHBOARD -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(0xFF030712),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
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
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (uiState.selectedTab == AppTab.HOME) {
                                            "Home · Office In"
                                        } else {
                                            "Profile"
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFF10B981)
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
                                        tint = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0B132B)
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF0B132B),
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
                                selectedIconColor = Color(0xFF00E5FF),
                                selectedTextColor = Color(0xFF00E5FF),
                                indicatorColor = Color(0xFF0F2238),
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier.testTag("tab_home")
                        )

                        NavigationBarItem(
                            selected = uiState.selectedTab == AppTab.PROFILE,
                            onClick = { viewModel.setTab(AppTab.PROFILE) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Profile"
                                )
                            },
                            label = { Text("Profile") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00E5FF),
                                selectedTextColor = Color(0xFF00E5FF),
                                indicatorColor = Color(0xFF0F2238),
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier.testTag("tab_profile")
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
                                challenges = uiState.challenges,
                                currentChallengeIndex = uiState.currentChallengeIndex,
                                challengeStatusText = uiState.challengeStatusText,
                                lastOfficeInTimestamp = uiState.officeInTimestamp,
                                onOfficeInClick = { viewModel.startOfficeIn() },
                                onCancelOfficeIn = { viewModel.cancelOfficeIn() },
                                onRestartChallenges = { viewModel.resetChallenges() },
                                onToggleCameraFacing = { viewModel.toggleCameraFacing() },
                                onMediaPipeDetected = { mpResult, bmp ->
                                    viewModel.onMediaPipeDetected(mpResult, bmp)
                                },
                                onFaceDetected = { face, w, h, bmp ->
                                    viewModel.onFaceDetected(face, w, h, bmp)
                                }
                            )
                        }

                        AppTab.PROFILE -> {
                            ProfilesScreen(
                                profiles = uiState.enrolledProfiles,
                                currentVector = uiState.currentVector,
                                metrics = uiState.metrics,
                                onAddProfile = { name, role, email, mobileNo, address, notes, color, photoUri ->
                                    viewModel.enrollProfileWithDetails(
                                        name = name,
                                        address = address,
                                        mobileNo = mobileNo,
                                        email = email,
                                        role = role,
                                        notes = notes,
                                        photoUri = photoUri,
                                        avatarColor = color
                                    )
                                },
                                onUpdateProfile = { updated -> viewModel.updateEnrolledProfile(updated) },
                                onDeleteProfile = { profileId -> viewModel.deleteEnrolledProfile(profileId) }
                            )
                        }
                    }
                }
            }
        }
    }
}
