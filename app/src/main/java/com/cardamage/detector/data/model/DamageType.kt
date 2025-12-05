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