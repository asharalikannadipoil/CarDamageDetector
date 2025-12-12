package com.cardamage.detector.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardamage.detector.data.model.*
import com.cardamage.detector.ui.components.FramePreviewCard
import com.cardamage.detector.ui.components.FrameDetailDialog
import com.cardamage.detector.ui.components.FrameTimelineView
import com.cardamage.detector.ui.components.MemoryMonitorCard
import com.cardamage.detector.ui.components.TimelineViewMode
import com.cardamage.detector.ui.viewmodel.VideoViewModel
import com.cardamage.detector.ui.viewmodel.VideoUiState
import com.cardamage.detector.video.VideoProcessingProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    onBackPressed: () -> Unit,
    onNavigateToVideoRecording: () -> Unit,
    preSelectedVideoUri: Uri? = null,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val frameResults by viewModel.frameResults.collectAsState()
    
    val listState = rememberLazyListState()
    var selectedFrame by remember { mutableStateOf<FrameAnalysisResult?>(null) }
    var timelineViewMode by remember { mutableStateOf(TimelineViewMode.HORIZONTAL_TIMELINE) }
    
    // Gallery video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.processVideo(it) }
    }

    // Auto-process pre-selected video URI
    LaunchedEffect(preSelectedVideoUri) {
        preSelectedVideoUri?.let { uri ->
            viewModel.processVideo(uri)
        }
    }

    // Auto-scroll to bottom when new frames are added
    LaunchedEffect(frameResults.size) {
        if (frameResults.isNotEmpty()) {
            listState.animateScrollToItem(frameResults.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top App Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Video Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video input selection (only show when not processing)
        if (uiState.currentVideoUri == null && processingProgress == null) {
            VideoInputSelection(
                onRecordVideo = onNavigateToVideoRecording,
                onSelectFromGallery = { videoPickerLauncher.launch("video/*") }
            )
        }

        // Memory monitor (show when processing or frames exist)
        if (frameResults.isNotEmpty() || processingProgress != null) {
            MemoryMonitorCard(
                memoryManager = viewModel.getMemoryManager(),
                showDetails = frameResults.size > 5,
                onCleanupClick = { viewModel.cleanupMemory() },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Processing status and results
        uiState.currentVideoUri?.let { videoUri ->
            VideoProcessingContent(
                videoUri = videoUri,
                processingProgress = processingProgress,
                frameResults = frameResults,
                uiState = uiState,
                onStopProcessing = { viewModel.stopProcessing() },
                listState = listState,
                selectedFrame = selectedFrame,
                timelineViewMode = timelineViewMode,
                onTimelineViewModeChange = { timelineViewMode = it },
                onFrameClick = { frameResult -> selectedFrame = frameResult }
            )
        }

        // Error handling
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Frame detail dialog
        selectedFrame?.let { frame ->
            FrameDetailDialog(
                frameResult = frame,
                onDismiss = { selectedFrame = null }
            )
        }
    }
}

@Composable
private fun VideoInputSelection(
    onRecordVideo: () -> Unit,
    onSelectFromGallery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = "Video",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Video Damage Analysis",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Record or select a video to analyze car damage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onRecordVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Record Video")
                }
                
                OutlinedButton(
                    onClick = onSelectFromGallery,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("From Gallery")
                }
            }
        }
    }
}

@Composable
private fun VideoProcessingContent(
    videoUri: Uri,
    processingProgress: VideoProcessingProgress?,
    frameResults: List<FrameAnalysisResult>,
    uiState: VideoUiState,
    onStopProcessing: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedFrame: FrameAnalysisResult?,
    timelineViewMode: TimelineViewMode,
    onTimelineViewModeChange: (TimelineViewMode) -> Unit,
    onFrameClick: (FrameAnalysisResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Processing status
        ProcessingStatusCard(
            processingProgress = processingProgress,
            totalFrames = frameResults.size,
            totalDetections = frameResults.sumOf { it.detections.size },
            onStopProcessing = onStopProcessing
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Frame results with timeline/grid view
        FrameTimelineView(
            frameResults = frameResults,
            selectedFrame = selectedFrame,
            onFrameClick = onFrameClick,
            viewMode = timelineViewMode,
            onViewModeChange = onTimelineViewModeChange,
            modifier = Modifier.weight(1f)
        )

        // Final results summary
        uiState.analysisResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            VideoAnalysisResultCard(result = result)
        }
    }
}

@Composable
private fun ProcessingStatusCard(
    processingProgress: VideoProcessingProgress?,
    totalFrames: Int,
    totalDetections: Int,
    onStopProcessing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (processingProgress) {
                is VideoProcessingProgress.Error -> MaterialTheme.colorScheme.errorContainer
                is VideoProcessingProgress.Completed -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (processingProgress) {
                    is VideoProcessingProgress.ExtractionStarted -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Extracting frames...")
                    }
                    is VideoProcessingProgress.ExtractionProgress -> {
                        CircularProgressIndicator(
                            progress = processingProgress.progress,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Extracting frames... ${(processingProgress.progress * 100).toInt()}%")
                    }
                    is VideoProcessingProgress.FrameProcessed -> {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "Processing",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Processing frames... $totalFrames processed")
                    }
                    is VideoProcessingProgress.Completed -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Analysis completed!")
                    }
                    is VideoProcessingProgress.Error -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Error: ${processingProgress.message}")
                    }
                    null -> {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = "Ready",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Ready to process")
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (processingProgress != null && 
                    processingProgress !is VideoProcessingProgress.Completed &&
                    processingProgress !is VideoProcessingProgress.Error) {
                    TextButton(onClick = onStopProcessing) {
                        Text("Stop")
                    }
                }
            }

            if (totalFrames > 0 || totalDetections > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Text(
                        text = "Frames: $totalFrames",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Detections: $totalDetections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun DetectionItem(detection: DamageDetection) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    Color(detection.type.color),
                    RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = detection.type.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(detection.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = detection.location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoAnalysisResultCard(result: VideoAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = "Summary",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Analysis Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.frames.size}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Frames Analyzed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.totalDetections}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Detections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${result.processingTimeMs / 1000}s",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Processing Time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}