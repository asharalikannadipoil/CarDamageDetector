package com.cardamage.detector.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardamage.detector.data.model.*
import com.cardamage.detector.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DamageOverlayImage(
    bitmap: android.graphics.Bitmap,
    detections: List<DamageDetection>,
    modifier: Modifier = Modifier,
    showConfidence: Boolean = true,
    showLabels: Boolean = true
) {
    val density = LocalDensity.current
    
    Box(modifier = modifier) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image with damage overlay",
            modifier = Modifier.fillMaxSize()
        )
        
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val scaleX = size.width / bitmap.width
            val scaleY = size.height / bitmap.height
            
            detections.forEach { detection ->
                drawDamageDetection(
                    detection = detection,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    showConfidence = showConfidence,
                    showLabels = showLabels,
                    density = density
                )
            }
        }
    }
}

private fun DrawScope.drawDamageDetection(
    detection: DamageDetection,
    scaleX: Float,
    scaleY: Float,
    showConfidence: Boolean,
    showLabels: Boolean,
    density: androidx.compose.ui.unit.Density
) {
    val boundingBox = detection.boundingBox
    
    val left = boundingBox.left * scaleX
    val top = boundingBox.top * scaleY
    val right = boundingBox.right * scaleX
    val bottom = boundingBox.bottom * scaleY
    
    val (severityColor, damageTypeColor) = getDamageColors(detection)
    
    // Enhanced damage type styling
    val strokeWidth = with(density) { 
        when (detection.severity) {
            DamageSeverity.MINOR -> 2.dp.toPx()
            DamageSeverity.MODERATE -> 3.dp.toPx()
            DamageSeverity.SEVERE -> 4.dp.toPx()
        }
    }
    
    // Draw bounding box
    drawRect(
        color = severityColor,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = strokeWidth)
    )
    
    // Draw corner markers
    val cornerSize = with(density) { 12.dp.toPx() }
    val cornerStroke = with(density) { 4.dp.toPx() }
    
    // Top-left corner
    drawLine(
        color = severityColor,
        start = Offset(left, top),
        end = Offset(left + cornerSize, top),
        strokeWidth = cornerStroke
    )
    drawLine(
        color = severityColor,
        start = Offset(left, top),
        end = Offset(left, top + cornerSize),
        strokeWidth = cornerStroke
    )
    
    // Top-right corner
    drawLine(
        color = severityColor,
        start = Offset(right, top),
        end = Offset(right - cornerSize, top),
        strokeWidth = cornerStroke
    )
    drawLine(
        color = severityColor,
        start = Offset(right, top),
        end = Offset(right, top + cornerSize),
        strokeWidth = cornerStroke
    )
    
    // Bottom-left corner
    drawLine(
        color = severityColor,
        start = Offset(left, bottom),
        end = Offset(left + cornerSize, bottom),
        strokeWidth = cornerStroke
    )
    drawLine(
        color = severityColor,
        start = Offset(left, bottom),
        end = Offset(left, bottom - cornerSize),
        strokeWidth = cornerStroke
    )
    
    // Bottom-right corner
    drawLine(
        color = severityColor,
        start = Offset(right, bottom),
        end = Offset(right - cornerSize, bottom),
        strokeWidth = cornerStroke
    )
    drawLine(
        color = severityColor,
        start = Offset(right, bottom),
        end = Offset(right, bottom - cornerSize),
        strokeWidth = cornerStroke
    )
    
    if (showLabels || showConfidence) {
        val labelText = buildString {
            if (showLabels) {
                append(detection.type.displayName)
                if (showConfidence) append(" - ")
            }
            if (showConfidence) {
                append("${(detection.confidence * 100).toInt()}%")
            }
        }
        
        val textPaint = android.graphics.Paint().apply {
            textSize = with(density) { 14.sp.toPx() }
            color = Color.White.toArgb()
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
        
        val labelBackgroundPadding = with(density) { 6.dp.toPx() }
        val labelLeft = left
        val labelTop = maxOf(top - textBounds.height() - labelBackgroundPadding * 2, 0f)
        
        // Draw label background
        drawRoundRect(
            color = severityColor,
            topLeft = Offset(labelLeft, labelTop),
            size = Size(
                textBounds.width() + labelBackgroundPadding * 2,
                textBounds.height() + labelBackgroundPadding * 2
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                with(density) { 4.dp.toPx() }
            )
        )
        
        // Draw text
        drawContext.canvas.nativeCanvas.drawText(
            labelText,
            labelLeft + labelBackgroundPadding,
            labelTop + textBounds.height() + labelBackgroundPadding,
            textPaint
        )
    }
    
    // Draw enhanced damage type indicator
    drawDamageTypeIndicator(
        detection = detection,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        damageTypeColor = damageTypeColor,
        density = density
    )
}

@Composable
fun DamageDetectionLegend(
    detections: List<DamageDetection>,
    modifier: Modifier = Modifier
) {
    if (detections.isEmpty()) return
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Detected Damages",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            detections.sortedByDescending { it.confidence }.forEach { detection ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val severityColor = when (detection.severity) {
                        DamageSeverity.MINOR -> DamageMinor
                        DamageSeverity.MODERATE -> DamageModerate
                        DamageSeverity.SEVERE -> DamageSevere
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .drawBehind { drawRect(severityColor) }
                    )
                    
                    Column {
                        Text(
                            text = detection.type.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = detection.severity.displayName,
                                fontSize = 10.sp,
                                color = severityColor
                            )
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(detection.confidence * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveImageWithOverlay(
    bitmap: android.graphics.Bitmap,
    detections: List<DamageDetection>,
    modifier: Modifier = Modifier,
    onDetectionClick: (DamageDetection) -> Unit = {}
) {
    var selectedDetection by remember { mutableStateOf<DamageDetection?>(null) }
    
    Box(modifier = modifier) {
        DamageOverlayImage(
            bitmap = bitmap,
            detections = detections,
            modifier = Modifier.fillMaxSize()
        )
        
        // Show detailed info for selected detection
        selectedDetection?.let { detection ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = detection.type.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Severity: ${detection.severity.displayName}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Confidence: ${(detection.confidence * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (detection.location.isNotEmpty()) {
                        Text(
                            text = "Location: ${detection.location}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    detection.roboflowClassName?.let { className ->
                        Text(
                            text = "Roboflow Class: $className",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    
                    TextButton(
                        onClick = { selectedDetection = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
        
        // Show legend
        if (detections.isNotEmpty()) {
            DamageDetectionLegend(
                detections = detections,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}

// Enhanced damage visualization helper functions

private fun DrawScope.drawDamageTypeIndicator(
    detection: DamageDetection,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    damageTypeColor: Color,
    density: androidx.compose.ui.unit.Density
) {
    val centerX = (left + right) / 2
    val centerY = (top + bottom) / 2
    val indicatorSize = with(density) { 16.dp.toPx() }
    
    when (detection.type.name.lowercase()) {
        "scratch" -> {
            // Draw scratch lines
            for (i in 0..2) {
                val offset = i * 3f
                drawLine(
                    color = damageTypeColor,
                    start = Offset(centerX - indicatorSize + offset, centerY - 6),
                    end = Offset(centerX + indicatorSize + offset, centerY + 6),
                    strokeWidth = 2f
                )
            }
        }
        "dent" -> {
            // Draw concentric circles for dent
            drawCircle(
                color = damageTypeColor.copy(alpha = 0.7f),
                radius = indicatorSize,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = damageTypeColor.copy(alpha = 0.5f),
                radius = indicatorSize * 0.6f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2f)
            )
        }
        "crack" -> {
            // Draw zigzag pattern for crack
            val points = mutableListOf<Offset>()
            for (i in 0..6) {
                val x = centerX - indicatorSize + (i * indicatorSize / 3)
                val y = centerY + if (i % 2 == 0) -6 else 6
                points.add(Offset(x, y))
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = damageTypeColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2f
                )
            }
        }
        "rust" -> {
            // Draw irregular shape for rust
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX - indicatorSize * 0.7f, centerY)
                lineTo(centerX - indicatorSize * 0.3f, centerY - indicatorSize * 0.5f)
                lineTo(centerX + indicatorSize * 0.2f, centerY - indicatorSize * 0.3f)
                lineTo(centerX + indicatorSize * 0.8f, centerY)
                lineTo(centerX + indicatorSize * 0.4f, centerY + indicatorSize * 0.6f)
                lineTo(centerX - indicatorSize * 0.2f, centerY + indicatorSize * 0.4f)
                close()
            }
            drawPath(
                path = path,
                color = damageTypeColor.copy(alpha = 0.8f),
                style = Stroke(width = 2f)
            )
        }
        "glass_damage" -> {
            // Draw shattered glass pattern
            val centerPoint = Offset(centerX, centerY)
            for (i in 0..5) {
                val angle = i * 60f * (kotlin.math.PI / 180f)
                val endX = centerX + cos(angle).toFloat() * indicatorSize
                val endY = centerY + sin(angle).toFloat() * indicatorSize
                drawLine(
                    color = damageTypeColor,
                    start = centerPoint,
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }
        }
        "paint_damage" -> {
            // Draw paint drip pattern
            drawCircle(
                color = damageTypeColor,
                radius = indicatorSize * 0.6f,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            drawLine(
                color = damageTypeColor,
                start = Offset(centerX, centerY + indicatorSize * 0.6f),
                end = Offset(centerX, centerY + indicatorSize),
                strokeWidth = 4f
            )
        }
        "bumper_damage" -> {
            // Draw broken rectangle for bumper
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX - indicatorSize, centerY - indicatorSize * 0.3f)
                lineTo(centerX - indicatorSize * 0.3f, centerY - indicatorSize * 0.6f)
                lineTo(centerX + indicatorSize * 0.4f, centerY - indicatorSize * 0.4f)
                lineTo(centerX + indicatorSize, centerY)
                lineTo(centerX + indicatorSize * 0.6f, centerY + indicatorSize * 0.5f)
                lineTo(centerX - indicatorSize * 0.5f, centerY + indicatorSize * 0.3f)
                close()
            }
            drawPath(
                path = path,
                color = damageTypeColor,
                style = Stroke(width = 2f)
            )
        }
        "door_damage" -> {
            // Draw door outline with damage
            drawRect(
                color = damageTypeColor,
                topLeft = Offset(centerX - indicatorSize * 0.7f, centerY - indicatorSize),
                size = Size(indicatorSize * 1.4f, indicatorSize * 2f),
                style = Stroke(width = 2f)
            )
            // Add damage mark
            drawCircle(
                color = damageTypeColor,
                radius = indicatorSize * 0.3f,
                center = Offset(centerX + indicatorSize * 0.2f, centerY),
                style = Stroke(width = 3f)
            )
        }
        else -> {
            // Default indicator - small circle
            drawCircle(
                color = damageTypeColor,
                radius = indicatorSize * 0.5f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun getDamageColors(detection: DamageDetection): Pair<Color, Color> {
    val severityColor = when (detection.severity) {
        DamageSeverity.MINOR -> Color(0xFF4CAF50)   // Green
        DamageSeverity.MODERATE -> Color(0xFFFF9800) // Orange  
        DamageSeverity.SEVERE -> Color(0xFFF44336)   // Red
    }
    
    val damageTypeColor = when (detection.type.name.lowercase()) {
        "scratch" -> Color(0xFFFF9800)      // Orange
        "dent" -> Color(0xFF2196F3)         // Blue
        "crack" -> Color(0xFFE91E63)        // Pink
        "rust" -> Color(0xFF795548)         // Brown
        "glass_damage" -> Color(0xFF00BCD4) // Cyan
        "paint_damage" -> Color(0xFF9C27B0) // Purple
        "bumper_damage" -> Color(0xFFFF5722) // Deep Orange
        "door_damage" -> Color(0xFF4CAF50)  // Green
        else -> Color(detection.type.color)
    }
    
    return Pair(severityColor, damageTypeColor)
}