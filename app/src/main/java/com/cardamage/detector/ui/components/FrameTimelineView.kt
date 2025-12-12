package com.cardamage.detector.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardamage.detector.data.model.*
import com.cardamage.detector.video.FrameExtractionReason

enum class TimelineViewMode {
    HORIZONTAL_TIMELINE,
    GRID_VIEW
}

@Composable
fun FrameTimelineView(
    frameResults: List<FrameAnalysisResult>,
    selectedFrame: FrameAnalysisResult?,
    onFrameClick: (FrameAnalysisResult) -> Unit,
    modifier: Modifier = Modifier,
    viewMode: TimelineViewMode = TimelineViewMode.HORIZONTAL_TIMELINE,
    onViewModeChange: (TimelineViewMode) -> Unit = {}
) {
    Column(modifier = modifier) {
        // Header with view mode toggle
        TimelineHeader(
            frameCount = frameResults.size,
            damageCount = frameResults.sumOf { it.detections.size },
            viewMode = viewMode,
            onViewModeChange = onViewModeChange
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Timeline content based on view mode
        when (viewMode) {
            TimelineViewMode.HORIZONTAL_TIMELINE -> {
                HorizontalTimeline(
                    frameResults = frameResults,
                    selectedFrame = selectedFrame,
                    onFrameClick = onFrameClick,
                    modifier = Modifier.height(120.dp)
                )
            }
            TimelineViewMode.GRID_VIEW -> {
                FrameGridView(
                    frameResults = frameResults,
                    selectedFrame = selectedFrame,
                    onFrameClick = onFrameClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    frameCount: Int,
    damageCount: Int,
    viewMode: TimelineViewMode,
    onViewModeChange: (TimelineViewMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Frame stats
            Column {
                Text(
                    text = "Video Frames",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$frameCount frames • $damageCount detections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // View mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onViewModeChange(TimelineViewMode.HORIZONTAL_TIMELINE) },
                    modifier = Modifier
                        .background(
                            if (viewMode == TimelineViewMode.HORIZONTAL_TIMELINE) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.ViewWeek,
                        contentDescription = "Timeline View",
                        tint = if (viewMode == TimelineViewMode.HORIZONTAL_TIMELINE)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(
                    onClick = { onViewModeChange(TimelineViewMode.GRID_VIEW) },
                    modifier = Modifier
                        .background(
                            if (viewMode == TimelineViewMode.GRID_VIEW) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "Grid View",
                        tint = if (viewMode == TimelineViewMode.GRID_VIEW)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalTimeline(
    frameResults: List<FrameAnalysisResult>,
    selectedFrame: FrameAnalysisResult?,
    onFrameClick: (FrameAnalysisResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to selected frame
    LaunchedEffect(selectedFrame) {
        selectedFrame?.let { frame ->
            val index = frameResults.indexOfFirst { it.frameIndex == frame.frameIndex }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }
    
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
    ) {
        items(frameResults) { frame ->
            TimelineFrameItem(
                frame = frame,
                isSelected = frame.frameIndex == selectedFrame?.frameIndex,
                onClick = { onFrameClick(frame) }
            )
        }
    }
}

@Composable
private fun FrameGridView(
    frameResults: List<FrameAnalysisResult>,
    selectedFrame: FrameAnalysisResult?,
    onFrameClick: (FrameAnalysisResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    
    // Auto-scroll to selected frame
    LaunchedEffect(selectedFrame) {
        selectedFrame?.let { frame ->
            val index = frameResults.indexOfFirst { it.frameIndex == frame.frameIndex }
            if (index != -1) {
                gridState.animateScrollToItem(index)
            }
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier
    ) {
        items(frameResults) { frame ->
            GridFrameItem(
                frame = frame,
                isSelected = frame.frameIndex == selectedFrame?.frameIndex,
                onClick = { onFrameClick(frame) }
            )
        }
    }
}

@Composable
private fun TimelineFrameItem(
    frame: FrameAnalysisResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        frame.errorMessage != null -> MaterialTheme.colorScheme.error
        frame.hasDetections() -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(80.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Thumbnail
            frame.thumbnailBitmap?.let { thumbnail ->
                if (!thumbnail.isRecycled) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Frame ${frame.frameIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Damage overlay indicators
                    if (frame.hasDetections()) {
                        frame.detections.forEach { detection ->
                            val bbox = detection.boundingBox
                            val scaleX = 60.dp.value / thumbnail.width
                            val scaleY = 60.dp.value / thumbnail.height
                            
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (bbox.left * scaleX).dp,
                                        y = (bbox.top * scaleY).dp
                                    )
                                    .size(
                                        width = ((bbox.right - bbox.left) * scaleX).dp,
                                        height = ((bbox.bottom - bbox.top) * scaleY).dp
                                    )
                                    .border(1.dp, Color(detection.type.color))
                            )
                        }
                    }
                }
            } ?: run {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "No preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            // Status indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                when {
                    frame.errorMessage != null -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    frame.hasDetections() -> {
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                        ) {
                            Text(
                                text = "${frame.detections.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                fontSize = 8.sp
                            )
                        }
                    }
                    else -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "No damage",
                            modifier = Modifier.size(12.dp),
                            tint = Color.Green
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Frame info
        Text(
            text = "${frame.frameIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = formatTimestamp(frame.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
        
        // Extraction reason indicator
        ExtractionReasonIndicator(frame.extractionReason)
    }
}

@Composable
private fun GridFrameItem(
    frame: FrameAnalysisResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        frame.errorMessage != null -> MaterialTheme.colorScheme.error
        frame.hasDetections() -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        border = if (isSelected) BorderStroke(2.dp, borderColor) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            frame.thumbnailBitmap?.let { thumbnail ->
                if (!thumbnail.isRecycled) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Frame ${frame.frameIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Damage overlays
                    if (frame.hasDetections()) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val scaleX = size.width / thumbnail.width
                            val scaleY = size.height / thumbnail.height
                            
                            frame.detections.forEach { detection ->
                                val bbox = detection.boundingBox
                                val left = bbox.left * scaleX
                                val top = bbox.top * scaleY
                                val right = bbox.right * scaleX
                                val bottom = bbox.bottom * scaleY
                                
                                drawRect(
                                    color = Color(detection.type.color),
                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "No preview",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Frame info overlay
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Frame ${frame.frameIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimestamp(frame.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
            
            // Status indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                when {
                    frame.errorMessage != null -> {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(2.dp),
                                tint = Color.White
                            )
                        }
                    }
                    frame.hasDetections() -> {
                        Surface(
                            color = Color.Red,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${frame.detections.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = Color.Green,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "No damage",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(2.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            
            // Extraction reason
            ExtractionReasonIndicator(
                reason = frame.extractionReason,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun ExtractionReasonIndicator(
    reason: FrameExtractionReason,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (reason) {
        FrameExtractionReason.FIRST_FRAME -> Icons.Default.PlayArrow to Color(0xFF2196F3)
        FrameExtractionReason.TIME_INTERVAL -> Icons.Default.Schedule to Color(0xFF9C27B0)
        FrameExtractionReason.SIDE_CHANGE -> Icons.Default.SwapHoriz to Color(0xFFFF9800)
    }
    
    Surface(
        color = color.copy(alpha = 0.8f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Icon(
            icon,
            contentDescription = reason.name,
            modifier = Modifier
                .size(16.dp)
                .padding(2.dp),
            tint = Color.White
        )
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}