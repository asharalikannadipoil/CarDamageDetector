package com.cardamage.detector.data.model

enum class DamageType(val displayName: String, val color: Long = 0xFFFF5722) {
    SCRATCH("Scratch", 0xFFFF9800),
    DENT("Dent", 0xFF2196F3),
    CRACK("Crack", 0xFFE91E63),
    RUST("Rust", 0xFF795548),
    GLASS_DAMAGE("Glass Damage", 0xFF00BCD4),
    PAINT_DAMAGE("Paint Damage", 0xFF9C27B0),
    BUMPER_DAMAGE("Bumper Damage", 0xFFFF5722),
    DOOR_DAMAGE("Door Damage", 0xFF4CAF50),
    UNKNOWN("Unknown", 0xFF757575)
}

enum class DamageSeverity(val displayName: String) {
    MINOR("Minor"),
    MODERATE("Moderate"),
    SEVERE("Severe")
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DamageDetection(
    val type: DamageType,
    val severity: DamageSeverity,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val location: String = "",
    val roboflowClassId: Int? = null,
    val roboflowClassName: String? = null
)

data class DamageAnalysisResult(
    val imagePath: String,
    val detections: List<DamageDetection>,
    val timestamp: Long = System.currentTimeMillis(),
    val processingTimeMs: Long = 0,
    val isRoboflowResult: Boolean = false,
    val errorMessage: String? = null
)

// Video-specific data models
data class VideoAnalysisResult(
    val videoPath: String,
    val videoUri: android.net.Uri,
    val frames: List<FrameAnalysisResult>,
    val totalDetections: Int,
    val videoDuration: Long,
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    fun getAllDetections(): List<DamageDetection> {
        return frames.flatMap { it.detections }
    }
    
    fun getDetectionsByType(): Map<DamageType, Int> {
        return getAllDetections().groupBy { it.type }.mapValues { it.value.size }
    }
    
    fun getSeverityDistribution(): Map<DamageSeverity, Int> {
        return getAllDetections().groupBy { it.severity }.mapValues { it.value.size }
    }
}

data class FrameAnalysisResult(
    val frameIndex: Int,
    val timestampMs: Long,
    val detections: List<DamageDetection>,
    val processingTimeMs: Long = 0,
    val extractionReason: com.cardamage.detector.video.FrameExtractionReason,
    val imagePath: String? = null,
    val errorMessage: String? = null,
    val frameBitmap: android.graphics.Bitmap? = null,     // Original frame for detailed view
    val thumbnailBitmap: android.graphics.Bitmap? = null  // Smaller preview for timeline
) {
    fun hasDetections(): Boolean = detections.isNotEmpty()
    
    fun getHighConfidenceDetections(threshold: Float = 0.7f): List<DamageDetection> {
        return detections.filter { it.confidence >= threshold }
    }
    
    fun getDamageStatusColor(): Long {
        return when {
            errorMessage != null -> 0xFFFF5722 // Red for errors
            hasDetections() -> 0xFFFF9800      // Orange for damage detected
            else -> 0xFF4CAF50                 // Green for no damage
        }
    }
    
    fun getMaxConfidence(): Float {
        return detections.maxOfOrNull { it.confidence } ?: 0f
    }
    
    fun releaseResources() {
        frameBitmap?.recycle()
        thumbnailBitmap?.recycle()
    }
}