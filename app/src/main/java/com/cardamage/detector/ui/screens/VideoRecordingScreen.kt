package com.cardamage.detector.ui.screens

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cardamage.detector.camera.CameraManager
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoRecordingScreen(
    onBackPressed: () -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    
    val outputDirectory = remember {
        File(context.getExternalFilesDir(null), "Videos").apply { mkdirs() }
    }

    // Recording timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        } else {
            recordingDuration = 0
        }
    }

    // Animated recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val manager = CameraManager(ctx, lifecycleOwner)
                    cameraManager = manager
                    manager.initializeCamera(
                        previewView = previewView,
                        captureMode = CameraManager.CaptureMode.VIDEO
                    ) { error ->
                        onError(error)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top bar with back button, flash, and recording indicator
        TopAppBar(
            title = {
                if (isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    Color.Red.copy(alpha = recordingAlpha),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDuration(recordingDuration),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isRecording) {
                            cameraManager?.stopVideoRecording()
                            isRecording = false
                        } else {
                            onBackPressed()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.ArrowBack,
                        contentDescription = if (isRecording) "Stop Recording" else "Back",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            actions = {
                if (cameraManager?.hasFlashUnit() == true && !isRecording) {
                    IconButton(
                        onClick = {
                            cameraManager?.toggleFlash()
                            isFlashOn = !isFlashOn
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        // Guidance overlay
        if (!isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .aspectRatio(16f / 9f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        Color.White.copy(alpha = 0.7f)
                    )
                ) {}
            }
        }
        
        // Instructions
        if (!isRecording) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp)
                    .padding(top = 120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Record video of car damage",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move around the vehicle to capture different angles",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Recording indicator overlay
        if (isRecording) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp)
                    .padding(top = 120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.8f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Recording",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording in progress...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Bottom controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Switch to photo mode button (only when not recording)
                if (!isRecording) {
                    IconButton(
                        onClick = {
                            cameraManager?.setCaptureMode(
                                CameraManager.CaptureMode.PHOTO,
                                previewView = PreviewView(context)
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Photo Mode",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Record/Stop button
                Button(
                    onClick = {
                        if (isRecording) {
                            cameraManager?.stopVideoRecording()
                            isRecording = false
                        } else {
                            cameraManager?.startVideoRecording(
                                outputDirectory = outputDirectory,
                                onVideoRecorded = { uri ->
                                    isRecording = false
                                    onVideoRecorded(uri)
                                },
                                onError = { error ->
                                    isRecording = false
                                    onError(error)
                                }
                            )
                            isRecording = true
                        }
                    },
                    modifier = Modifier
                        .size(if (isRecording) 80.dp else 72.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        modifier = Modifier.size(if (isRecording) 40.dp else 32.dp)
                    )
                }
                
                // Duration indicator or spacer
                if (!isRecording) {
                    Spacer(modifier = Modifier.size(48.dp))
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatDuration(recordingDuration),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                cameraManager?.stopVideoRecording()
            }
            cameraManager?.shutdown()
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}