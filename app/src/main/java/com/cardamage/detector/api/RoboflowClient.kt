package com.cardamage.detector.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cardamage.detector.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoboflowClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RoboflowClient"
        private const val BASE_URL = "https://detect.roboflow.com/"
        private const val DEFAULT_API_KEY = "" // Will be loaded from resources or config
        
        // Damage type mapping for Roboflow response
        private val ROBOFLOW_CLASS_MAPPING = mapOf(
            "scratch" to DamageType.SCRATCH,
            "dent" to DamageType.DENT,
            "crack" to DamageType.CRACK,
            "rust" to DamageType.RUST,
            "paint_damage" to DamageType.PAINT_DAMAGE,
            "glass_damage" to DamageType.GLASS_DAMAGE,
            "bumper_damage" to DamageType.BUMPER_DAMAGE,
            "door_damage" to DamageType.DOOR_DAMAGE
        )
    }
    
    private val apiService: RoboflowApiService by lazy {
        createApiService()
    }
    
    private fun createApiService(): RoboflowApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(RoboflowApiService::class.java)
    }
    
    /**
     * Detect damage using Roboflow API
     */
    suspend fun detectDamage(
        bitmap: Bitmap,
        imagePath: String,
        apiKey: String = DEFAULT_API_KEY
    ): DamageAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting Roboflow damage detection for: $imagePath")
            
            if (apiKey.isBlank()) {
                Log.e(TAG, "API key is required for Roboflow integration")
                return@withContext createErrorResult(imagePath, startTime, "API key not configured")
            }
            
            // Convert bitmap to multipart body
            val imageBody = bitmapToMultipartBody(bitmap)
            
            // Make API request
            val response = apiService.detectDamage(
                image = imageBody,
                apiKey = apiKey,
                confidence = 0.3f,
                overlap = 0.45f
            )
            
            if (response.isSuccessful && response.body() != null) {
                val roboflowResponse = response.body()!!
                Log.d(TAG, "Roboflow API success: ${roboflowResponse.predictions.size} detections")
                
                // Convert Roboflow response to our format
                val detections = convertRoboflowPredictions(roboflowResponse, bitmap.width, bitmap.height)
                
                val processingTime = System.currentTimeMillis() - startTime
                
                DamageAnalysisResult(
                    imagePath = imagePath,
                    detections = detections,
                    processingTimeMs = processingTime,
                    isRoboflowResult = true
                )
            } else {
                Log.e(TAG, "Roboflow API error: ${response.code()} - ${response.message()}")
                createErrorResult(imagePath, startTime, "API error: ${response.code()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Roboflow detection: ${e.message}", e)
            createErrorResult(imagePath, startTime, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Get model information from Roboflow
     */
    suspend fun getModelInfo(apiKey: String = DEFAULT_API_KEY): RoboflowModelInfo? = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                Log.e(TAG, "API key required for model info")
                return@withContext null
            }
            
            val response = apiService.getModelInfo(apiKey)
            if (response.isSuccessful) {
                Log.d(TAG, "Model info retrieved successfully")
                response.body()
            } else {
                Log.e(TAG, "Failed to get model info: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model info: ${e.message}", e)
            null
        }
    }
    
    private fun bitmapToMultipartBody(bitmap: Bitmap): MultipartBody.Part {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()
        
        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", "image.jpg", requestBody)
    }
    
    private fun convertRoboflowPredictions(
        roboflowResponse: RoboflowDetectionResponse,
        originalWidth: Int,
        originalHeight: Int
    ): List<DamageDetection> {
        return roboflowResponse.predictions.map { prediction ->
            val damageType = ROBOFLOW_CLASS_MAPPING[prediction.`class`.lowercase()] ?: DamageType.UNKNOWN
            val severity = determineSeverity(prediction.confidence.toFloat(), damageType)
            
            // Convert Roboflow coordinates (center x, y, width, height) to bounding box
            val left = (prediction.x - prediction.width / 2).toFloat()
            val top = (prediction.y - prediction.height / 2).toFloat()
            val right = (prediction.x + prediction.width / 2).toFloat()
            val bottom = (prediction.y + prediction.height / 2).toFloat()
            
            val location = determineLocation(left, top, right, bottom, originalWidth, originalHeight)
            
            DamageDetection(
                type = damageType,
                severity = severity,
                confidence = prediction.confidence.toFloat(),
                boundingBox = BoundingBox(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom
                ),
                location = location,
                roboflowClassId = prediction.class_id,
                roboflowClassName = prediction.`class`
            )
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
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        
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
    
    private fun createErrorResult(
        imagePath: String,
        startTime: Long,
        errorMessage: String
    ): DamageAnalysisResult {
        return DamageAnalysisResult(
            imagePath = imagePath,
            detections = emptyList(),
            processingTimeMs = System.currentTimeMillis() - startTime,
            isRoboflowResult = false,
            errorMessage = errorMessage
        )
    }
}