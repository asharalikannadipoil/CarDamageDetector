package com.cardamage.detector

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cardamage.detector.ui.screens.*
import com.cardamage.detector.ui.theme.CarDamageDetectorTheme
import com.cardamage.detector.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            CarDamageDetectorTheme {
                DamageDetectorApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageDetectorApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val roboflowApiKey by viewModel.roboflowApiKey.collectAsStateWithLifecycle()
    val useRoboflow by viewModel.useRoboflow.collectAsStateWithLifecycle()
    
    // Show error messages as toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    // Navigate to results when analysis completes
    LaunchedEffect(uiState.analysisCompleted, analysisResult) {
        if (uiState.analysisCompleted && analysisResult != null) {
            navController.navigate("results") {
                popUpTo("home") { inclusive = false }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    onCameraClick = { 
                        navController.navigate("camera")
                    },
                    onImageSelected = { uri -> 
                        viewModel.analyzeImage(uri)
                    },
                    onVideoClick = {
                        navController.navigate("video")
                    },
                    onHistoryClick = { 
                        navController.navigate("history")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }
            
            composable("camera") {
                CameraScreen(
                    onBackPressed = { 
                        navController.popBackStack()
                    },
                    onImageCaptured = { uri -> 
                        viewModel.analyzeImage(uri)
                        navController.popBackStack()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
            
            composable("results") {
                analysisResult?.let { result ->
                    ResultScreen(
                        result = result,
                        bitmap = currentBitmap,
                        onBackPressed = { 
                            viewModel.resetAnalysis()
                            navController.popBackStack()
                        },
                        onSaveResult = {
                            Toast.makeText(context, "Result saved to history", Toast.LENGTH_SHORT).show()
                        },
                        onShareResult = {
                            // TODO: Implement sharing functionality
                            Toast.makeText(context, "Share functionality coming soon", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            
            composable("history") {
                HistoryScreen(
                    onBackPressed = { 
                        navController.popBackStack()
                    }
                )
            }
            
            composable(
                route = "video?videoUri={videoUri}",
                arguments = listOf(
                    navArgument("videoUri") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val videoUriString = backStackEntry.arguments?.getString("videoUri")
                val videoUri = videoUriString?.let { Uri.parse(it) }
                
                VideoScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    onNavigateToVideoRecording = {
                        navController.navigate("video_recording")
                    },
                    preSelectedVideoUri = videoUri
                )
            }
            
            composable("video_recording") {
                VideoRecordingScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    onVideoRecorded = { uri ->
                        // Navigate to video screen with the recorded video URI
                        val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                        navController.navigate("video?videoUri=$encodedUri") {
                            popUpTo("video") { inclusive = true }
                        }
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
            
            composable("settings") {
                SettingsScreen(
                    apiKey = roboflowApiKey,
                    useRoboflow = useRoboflow,
                    onApiKeyChanged = { key ->
                        viewModel.setRoboflowApiKey(key)
                    },
                    onRoboflowToggled = { enabled ->
                        viewModel.toggleRoboflowUsage(enabled)
                    },
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        // Show loading overlay
        if (uiState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                                Text(
                                    text = "Analyzing image...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}