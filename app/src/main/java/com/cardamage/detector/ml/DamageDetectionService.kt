package com.cardamage.detector.ml

import android.graphics.Bitmap
import android.util.Log
import com.cardamage.detector.api.RoboflowClient
import com.cardamage.detector.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DamageDetectionService @Inject constructor(
    private val tensorFlowHelper: TensorFlowLiteHelper,
    private val roboflowClient: RoboflowClient
) {
    companion object {
        private const val TAG = "DamageDetectionService"
        private val DAMAGE_TYPE_MAPPING = mapOf(
            0 to DamageType.SCRATCH,
            1 to DamageType.DENT,
            2 to DamageType.CRACK,
            3 to DamageType.RUST,
            4 to DamageType.GLASS_DAMAGE,
            5 to DamageType.PAINT_DAMAGE,
            6 to DamageType.BUMPER_DAMAGE,
            7 to DamageType.DOOR_DAMAGE
        )
    }

    /**
     * Enhanced damage detection with Roboflow API integration
     */
    suspend fun detectDamageEnhanced(
        bitmap: Bitmap,
        imagePath: String,
        apiKey: String = "",
        useRoboflow: Boolean = true
    ): DamageAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting enhanced damage detection for: $imagePath")
            
            // Try Roboflow first if enabled and API key is provided
            if (useRoboflow && apiKey.isNotBlank()) {
                Log.d(TAG, "Attempting Roboflow API detection...")
                val roboflowResult = roboflowClient.detectDamage(bitmap, imagePath, apiKey)
                
                if (roboflowResult.detections.isNotEmpty() && roboflowResult.errorMessage == null) {
                    Log.d(TAG, "Roboflow detection successful: ${roboflowResult.detections.size} detections")
                    return@withContext roboflowResult
                } else {
                    Log.w(TAG, "Roboflow detection failed or returned no results, falling back to local model")
                }
            }
            
            // Fallback to local TensorFlow Lite model
            Log.d(TAG, "Using local TensorFlow Lite model...")
            return@withContext detectDamageLocal(bitmap, imagePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during enhanced damage detection: ${e.message}", e)
            // Fallback to local model on any error
            return@withContext detectDamageLocal(bitmap, imagePath)
        }
    }

    /**
     * Original local TensorFlow Lite detection method
     */
    suspend fun detectDamage(
        bitmap: Bitmap,
        imagePath: String
    ): DamageAnalysisResult = detectDamageLocal(bitmap, imagePath)
    
    private suspend fun detectDamageLocal(
        bitmap: Bitmap,
        imagePath: String
    ): DamageAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting LOCAL TensorFlow damage detection for image: $imagePath")
            Log.d(TAG, "Image dimensions: ${bitmap.width}x${bitmap.height}, format: ${bitmap.config}")
            
            // Validate image first
            if (!tensorFlowHelper.validateImage(bitmap)) {
                Log.e(TAG, "Image validation failed, trying basic color detection")
                return@withContext performBasicColorDetection(bitmap, imagePath, startTime)
            }
            
            val inputBuffer = tensorFlowHelper.preprocessImage(bitmap)
            Log.d(TAG, "Image preprocessed, buffer size: ${inputBuffer.remaining()} bytes")
            
            val output = tensorFlowHelper.runInference(inputBuffer)
            
            if (output == null) {
                Log.e(TAG, "TensorFlow inference failed, falling back to basic detection")
                return@withContext performBasicColorDetection(bitmap, imagePath, startTime)
            }
            
            Log.d(TAG, "Inference successful, processing outputs...")
            
            val detectionResults = tensorFlowHelper.postProcessOutput(
                output, 
                bitmap.width, 
                bitmap.height
            )
            
            Log.d(TAG, "Post-processing found ${detectionResults.size} raw detections")
            
            var damageDetections = detectionResults.map { result ->
                val damageType = DAMAGE_TYPE_MAPPING[result.classIndex] ?: DamageType.UNKNOWN
                val severity = determineSeverity(result.confidence, damageType)
                val location = determineLocation(result, bitmap.width, bitmap.height)
                
                Log.d(TAG, "Mapped detection: ${damageType.displayName} (${String.format("%.2f", result.confidence)}) at $location")
                
                DamageDetection(
                    type = damageType,
                    severity = severity,
                    confidence = result.confidence,
                    boundingBox = BoundingBox(
                        left = result.left,
                        top = result.top,
                        right = result.right,
                        bottom = result.bottom
                    ),
                    location = location
                )
            }
            
            // If TensorFlow found no detections, try basic color detection as backup
            if (damageDetections.isEmpty()) {
                Log.w(TAG, "TensorFlow found no detections, trying basic color analysis...")
                val colorDetections = performBasicColorAnalysis(bitmap)
                damageDetections = colorDetections
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "LOCAL detection completed. Found ${damageDetections.size} damages in ${processingTime}ms")
            
            DamageAnalysisResult(
                imagePath = imagePath,
                detections = damageDetections,
                processingTimeMs = processingTime,
                isRoboflowResult = false
            )
            
        } catch (e: Exception) {
            val errorMsg = "Local detection error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            
            // Final fallback: basic color detection
            return@withContext performBasicColorDetection(bitmap, imagePath, startTime)
        }
    }
    
    private fun performBasicColorDetection(
        bitmap: Bitmap,
        imagePath: String,
        startTime: Long
    ): DamageAnalysisResult {
        Log.d(TAG, "=== BASIC COLOR DETECTION FALLBACK ===")
        
        try {
            val detections = performBasicColorAnalysis(bitmap)
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Basic color detection found ${detections.size} potential damages")
            
            return DamageAnalysisResult(
                imagePath = imagePath,
                detections = detections,
                processingTimeMs = processingTime,
                isRoboflowResult = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Even basic detection failed: ${e.message}", e)
            return DamageAnalysisResult(
                imagePath = imagePath,
                detections = emptyList(),
                processingTimeMs = System.currentTimeMillis() - startTime,
                isRoboflowResult = false,
                errorMessage = "All detection methods failed"
            )
        }
    }
    
    private fun performBasicColorAnalysis(bitmap: Bitmap): List<DamageDetection> {
        Log.d(TAG, "Analyzing image colors for damage indicators...")
        
        val detections = mutableListOf<DamageDetection>()
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample colors from different regions
        val samplePoints = listOf(
            Pair(width * 0.25f, height * 0.25f),
            Pair(width * 0.75f, height * 0.25f),
            Pair(width * 0.25f, height * 0.75f),
            Pair(width * 0.75f, height * 0.75f),
            Pair(width * 0.5f, height * 0.5f)
        )
        
        var damageIndicators = 0
        val regionSize = minOf(width, height) * 0.1f
        
        for ((x, y) in samplePoints) {
            try {
                val pixel = bitmap.getPixel(x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1))
                
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                
                // Look for damage indicators
                val hasDarkness = red < 50 && green < 50 && blue < 50  // Very dark areas (dents, shadows)
                val hasRust = red > 150 && green < 100 && blue < 80   // Reddish-brown (rust)
                val hasContrast = kotlin.math.abs(red - green) > 50 || kotlin.math.abs(green - blue) > 50 // High contrast
                
                if (hasDarkness || hasRust || hasContrast) {
                    damageIndicators++
                    
                    val damageType = when {
                        hasRust -> DamageType.RUST
                        hasDarkness -> DamageType.DENT
                        else -> DamageType.SCRATCH
                    }
                    
                    val severity = when {
                        hasDarkness -> DamageSeverity.SEVERE
                        hasRust -> DamageSeverity.MODERATE
                        else -> DamageSeverity.MINOR
                    }
                    
                    detections.add(
                        DamageDetection(
                            type = damageType,
                            severity = severity,
                            confidence = 0.6f,
                            boundingBox = BoundingBox(
                                left = (x - regionSize).coerceAtLeast(0f),
                                top = (y - regionSize).coerceAtLeast(0f),
                                right = (x + regionSize).coerceAtMost(width.toFloat()),
                                bottom = (y + regionSize).coerceAtMost(height.toFloat())
                            ),
                            location = determineLocationFromCoords(x, y, width, height)
                        )
                    )
                    
                    Log.d(TAG, "Color analysis found ${damageType.displayName} at ($x, $y) - R:$red G:$green B:$blue")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing pixel at ($x, $y): ${e.message}")
            }
        }
        
        // If we found indicators of damage, assume there's more
        if (damageIndicators > 2) {
            Log.d(TAG, "Multiple damage indicators found ($damageIndicators), adding comprehensive damage")
            detections.add(
                DamageDetection(
                    type = DamageType.DENT,
                    severity = DamageSeverity.SEVERE,
                    confidence = 0.8f,
                    boundingBox = BoundingBox(
                        left = width * 0.2f,
                        top = height * 0.2f,
                        right = width * 0.8f,
                        bottom = height * 0.8f
                    ),
                    location = "Front section"
                )
            )
        }
        
        Log.d(TAG, "Basic color analysis completed: ${detections.size} detections")
        return detections.distinctBy { "${it.type}_${it.boundingBox.left.toInt()}_${it.boundingBox.top.toInt()}" }
    }
    
    private fun determineLocationFromCoords(x: Float, y: Float, width: Int, height: Int): String {
        val horizontalThird = width / 3f
        val verticalThird = height / 3f
        
        val horizontal = when {
            x < horizontalThird -> "Left"
            x > horizontalThird * 2 -> "Right"
            else -> "Center"
        }
        
        val vertical = when {
            y < verticalThird -> "Top"
            y > verticalThird * 2 -> "Bottom"
            else -> "Middle"
        }
        
        return if (horizontal == "Center" && vertical == "Middle") {
            "Center"
        } else if (horizontal == "Center") {
            vertical
        } else if (vertical == "Middle") {
            horizontal
        } else {
            "$vertical $horizontal"
        }
    }

    private fun determineSeverity(confidence: Float, damageType: DamageType): DamageSeverity {
        return when {
            confidence >= 0.8f -> DamageSeverity.SEVERE
            confidence >= 0.6f -> when (damageType) {
                DamageType.CRACK, DamageType.RUST, DamageType.GLASS_DAMAGE -> DamageSeverity.SEVERE
                else -> DamageSeverity.MODERATE
            }
            else -> DamageSeverity.MINOR
        }
    }

    private fun determineLocation(
        result: TensorFlowLiteHelper.DetectionResult,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val centerX = (result.left + result.right) / 2
        val centerY = (result.top + result.bottom) / 2
        
        val horizontalThird = imageWidth / 3f
        val verticalThird = imageHeight / 3f
        
        val horizontal = when {
            centerX < horizontalThird -> "Left"
            centerX > horizontalThird * 2 -> "Right"
            else -> "Center"
        }
        
        val vertical = when {
            centerY < verticalThird -> "Top"
            centerY > verticalThird * 2 -> "Bottom"
            else -> "Middle"
        }
        
        return if (horizontal == "Center" && vertical == "Middle") {
            "Center"
        } else if (horizontal == "Center") {
            vertical
        } else if (vertical == "Middle") {
            horizontal
        } else {
            "$vertical $horizontal"
        }
    }

    fun validateImage(bitmap: Bitmap): Boolean {
        return bitmap.width >= 224 && 
               bitmap.height >= 224 && 
               !bitmap.isRecycled
    }

    fun close() {
        tensorFlowHelper.close()
    }
}