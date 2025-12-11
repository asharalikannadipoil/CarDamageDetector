package com.cardamage.detector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cardamage.detector.data.model.*

@Composable
fun FrameDetailDialog(
    frameResult: FrameAnalysisResult,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frame ${frameResult.frameIndex + 1} Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Frame image with damage overlay
                    frameResult.frameBitmap?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                DamageOverlayImage(
                                    bitmap = bitmap,
                                    detections = frameResult.detections,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Frame metadata
                    FrameMetadataSection(frameResult)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Damage analysis section
                    DamageAnalysisSection(frameResult)
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameMetadataSection(frameResult: FrameAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Frame Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            MetadataRow("Timestamp", formatTimestamp(frameResult.timestampMs))
            MetadataRow("Frame Index", "${frameResult.frameIndex + 1}")
            MetadataRow("Processing Time", "${frameResult.processingTimeMs}ms")
            MetadataRow(
                "Extraction Reason", 
                when (frameResult.extractionReason) {
                    com.cardamage.detector.video.FrameExtractionReason.FIRST_FRAME -> "First Frame"
                    com.cardamage.detector.video.FrameExtractionReason.TIME_INTERVAL -> "30-second interval"
                    com.cardamage.detector.video.FrameExtractionReason.SIDE_CHANGE -> "Vehicle side change"
                }
            )
            
            frameResult.errorMessage?.let { error ->
                MetadataRow("Error", error, isError = true)
            }
        }
    }
}

@Composable
private fun DamageAnalysisSection(frameResult: FrameAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                frameResult.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                frameResult.hasDetections() -> Color(0xFFFF9800).copy(alpha = 0.1f)
                else -> Color(0xFF4CAF50).copy(alpha = 0.1f)
            }
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
                val (icon, color, title) = when {
                    frameResult.errorMessage != null -> Triple(
                        Icons.Default.Error,
                        MaterialTheme.colorScheme.error,
                        "Analysis Failed"
                    )
                    frameResult.hasDetections() -> Triple(
                        Icons.Default.Warning,
                        Color(0xFFFF9800),
                        "Damage Detected"
                    )
                    else -> Triple(
                        Icons.Default.CheckCircle,
                        Color(0xFF4CAF50),
                        "No Damage"
                    )
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (frameResult.hasDetections()) {
                Text(
                    text = "${frameResult.detections.size} damage detection(s) found:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                frameResult.detections.forEach { detection ->
                    DetectionDetailItem(detection)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Max Confidence: ${(frameResult.getMaxConfidence() * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (frameResult.errorMessage == null) {
                Text(
                    text = "No damage was detected in this frame.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetectionDetailItem(detection: DamageDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(detection.type.color).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            Color(detection.type.color),
                            RoundedCornerShape(6.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detection.type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(detection.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(detection.type.color)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Severity: ${detection.severity.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (detection.location.isNotBlank()) {
                Text(
                    text = "Location: ${detection.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Bounding box coordinates
            val bbox = detection.boundingBox
            Text(
                text = "Coordinates: (${bbox.left.toInt()}, ${bbox.top.toInt()}) to (${bbox.right.toInt()}, ${bbox.bottom.toInt()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}