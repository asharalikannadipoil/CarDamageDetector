package com.cardamage.detector.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cardamage.detector.data.model.*
import com.cardamage.detector.video.FrameExtractionReason

@Composable
fun FramePreviewCard(
    frameResult: FrameAnalysisResult,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val statusColor = Color(frameResult.getDamageStatusColor())
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, statusColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Frame thumbnail with overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Display thumbnail if available
                frameResult.thumbnailBitmap?.let { thumbnail ->
                    if (!thumbnail.isRecycled) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = "Frame ${frameResult.frameIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            
                            // Overlay damage markers on thumbnail
                            if (frameResult.hasDetections()) {
                                DamageOverlayThumbnail(
                                    detections = frameResult.detections,
                                    bitmapWidth = thumbnail.width,
                                    bitmapHeight = thumbnail.height,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                } ?: run {
                    // Fallback when no thumbnail available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "No preview",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                
                // Status indicator overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    when {
                        frameResult.errorMessage != null -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Red
                            )
                        }
                        frameResult.hasDetections() -> {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                            ) {
                                Text(
                                    text = "${frameResult.detections.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "No damage",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Green
                            )
                        }
                    }
                }
            }
            
            // Frame information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Frame header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frame ${frameResult.frameIndex + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimestamp(frameResult.timestampMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Extraction reason badge
                ExtractionReasonBadge(reason = frameResult.extractionReason)
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Damage summary
                when {
                    frameResult.errorMessage != null -> {
                        Text(
                            text = "Error: ${frameResult.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    frameResult.hasDetections() -> {
                        Column {
                            Text(
                                text = "${frameResult.detections.size} damage(s) detected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF9800)
                            )
                            
                            // Show top detection
                            frameResult.detections.maxByOrNull { it.confidence }?.let { topDetection ->
                                Text(
                                    text = "${topDetection.type.displayName} (${(topDetection.confidence * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "No damage detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractionReasonBadge(reason: FrameExtractionReason) {
    val (text, color) = when (reason) {
        FrameExtractionReason.FIRST_FRAME -> "First" to Color(0xFF2196F3)
        FrameExtractionReason.TIME_INTERVAL -> "30s" to Color(0xFF9C27B0)
        FrameExtractionReason.SIDE_CHANGE -> "Side Change" to Color(0xFFFF9800)
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DamageOverlayThumbnail(
    detections: List<DamageDetection>,
    bitmapWidth: Int,
    bitmapHeight: Int,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
    ) {
        val scaleX = size.width / bitmapWidth
        val scaleY = size.height / bitmapHeight
        
        detections.forEach { detection ->
            val bbox = detection.boundingBox
            
            // Scale bounding box to canvas size
            val left = bbox.left * scaleX
            val top = bbox.top * scaleY
            val right = bbox.right * scaleX
            val bottom = bbox.bottom * scaleY
            
            // Draw bounding box
            drawRect(
                color = Color(detection.type.color),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
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