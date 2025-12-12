package com.cardamage.detector.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardamage.detector.video.MemoryManager
import kotlinx.coroutines.delay

@Composable
fun MemoryMonitorCard(
    memoryManager: MemoryManager,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false,
    onCleanupClick: (() -> Unit)? = null
) {
    var memoryStats by remember { mutableStateOf(memoryManager.getMemoryStats()) }
    
    // Update memory stats periodically
    LaunchedEffect(Unit) {
        while (true) {
            memoryStats = memoryManager.getMemoryStats()
            delay(2000) // Update every 2 seconds
        }
    }
    
    val usageColor by animateColorAsState(
        targetValue = when {
            memoryStats.usagePercentage > 90f -> Color(0xFFF44336) // Red
            memoryStats.usagePercentage > 70f -> Color(0xFFFF9800) // Orange
            else -> Color(0xFF4CAF50) // Green
        },
        label = "usage_color"
    )
    
    val progressAnimation by animateFloatAsState(
        targetValue = memoryStats.usagePercentage / 100f,
        label = "progress_animation"
    )
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (memoryStats.isNearLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (memoryStats.isNearLimit) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (memoryStats.isNearLimit) Icons.Default.Warning else Icons.Default.Memory,
                        contentDescription = "Memory",
                        tint = usageColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Memory Usage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Cleanup button
                if (onCleanupClick != null && memoryStats.usagePercentage > 50f) {
                    IconButton(
                        onClick = onCleanupClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Clean Memory",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Memory usage bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${memoryStats.totalMemoryMB.toInt()}MB",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = usageColor
                    )
                    Text(
                        text = "${memoryStats.usagePercentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressAnimation)
                            .background(usageColor)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Max: ${memoryStats.maxMemoryMB.toInt()}MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            
            // Detailed breakdown
            if (showDetails && memoryStats.totalBitmaps > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MemoryDetailItem(
                        label = "Thumbnails",
                        count = memoryStats.thumbnails,
                        icon = Icons.Default.PhotoSizeSelectSmall
                    )
                    MemoryDetailItem(
                        label = "Previews", 
                        count = memoryStats.previews,
                        icon = Icons.Default.Preview
                    )
                    MemoryDetailItem(
                        label = "Originals",
                        count = memoryStats.originals,
                        icon = Icons.Default.Image
                    )
                }
            }
            
            // Warning message for high usage
            if (memoryStats.usagePercentage > 85f) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (memoryStats.usagePercentage > 95f) {
                            "Memory critical! Cleanup recommended"
                        } else {
                            "Memory usage high"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryDetailItem(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}