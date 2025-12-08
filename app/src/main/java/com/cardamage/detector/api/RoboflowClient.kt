package com.cardamage.detector.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.cardamage.detector.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
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
        private const val BASE_URL = "https://serverless.roboflow.com/"
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
            
            // Convert bitmap to base64
            val base64Image = bitmapToBase64(bitmap)
            
            // Create workflow request
            val workflowRequest = RoboflowWorkflowRequest(
                api_key = apiKey,
                inputs = RoboflowInputs(
                    image = RoboflowImageInput(
                        type = "base64",
                        value = base64Image
                    )
                )
            )
            
            // Make API request
            val response = apiService.detectDamage(workflowRequest)
            
            // Log raw response for debugging
            Log.d(TAG, "Response code: ${response.code()}")
            Log.d(TAG, "Response headers: ${response.headers()}")
            
            if (response.isSuccessful) {
                val rawResponse = response.raw().toString()
                Log.d(TAG, "Raw response: $rawResponse")
                
                // Try to get raw JSON string for analysis
                try {
                    // Create a new response to read the raw JSON
                    val responseBodyString = response.raw().body?.string()
                    Log.d(TAG, "Raw JSON response: $responseBodyString")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading raw response body: ${e.message}")
                }
                
                if (response.body() != null) {
                    val workflowResponse = response.body()!!
                    
                    if (workflowResponse.error != null) {
                        Log.e(TAG, "Workflow error: ${workflowResponse.error}")
                        return@withContext createErrorResult(imagePath, startTime, "Workflow error: ${workflowResponse.error}")
                    } else {
                        // Extract predictions from the array of outputs
                        val allPredictions = mutableListOf<RoboflowPrediction>()
                        
                        workflowResponse.outputs?.forEachIndexed { index, output ->
                            Log.d(TAG, "Processing output $index: ${output}")
                            
                            output.predictions?.let { predictionsElement ->
                                Log.d(TAG, "Predictions element type: ${predictionsElement.javaClass.simpleName}")
                                Log.d(TAG, "Predictions content: $predictionsElement")
                                
                                val extractedPredictions = extractPredictionsFromJson(predictionsElement)
                                allPredictions.addAll(extractedPredictions)
                                Log.d(TAG, "Extracted ${extractedPredictions.size} predictions from output $index")
                            }
                        }
                        
                        Log.d(TAG, "Roboflow workflow success: ${allPredictions.size} detections from ${workflowResponse.outputs?.size ?: 0} outputs")
                        
                        // Convert workflow predictions to our format
                        val detections = convertWorkflowPredictions(allPredictions, bitmap.width, bitmap.height)
                        
                        val processingTime = System.currentTimeMillis() - startTime
                        
                        return@withContext DamageAnalysisResult(
                            imagePath = imagePath,
                            detections = detections,
                            processingTimeMs = processingTime,
                            isRoboflowResult = true
                        )
                    }
                } else {
                    Log.e(TAG, "Response body is null despite successful status code")
                    return@withContext createErrorResult(imagePath, startTime, "Empty response body")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Roboflow API error: ${response.code()} - ${response.message()}")
                Log.e(TAG, "Error body: $errorBody")
                when (response.code()) {
                    403 -> Log.e(TAG, "API key doesn't have access to this model. Check your API key permissions.")
                    404 -> Log.e(TAG, "Model not found. The endpoint may be incorrect.")
                    429 -> Log.e(TAG, "Rate limit exceeded. Too many requests.")
                    else -> Log.e(TAG, "Unexpected API error: ${response.code()}")
                }
                createErrorResult(imagePath, startTime, "API error: ${response.code()} - ${response.message()}")
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
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    private fun extractPredictionsFromJson(predictionsElement: JsonElement): List<RoboflowPrediction> {
        val predictions = mutableListOf<RoboflowPrediction>()
        val gson = Gson()
        
        try {
            when {
                predictionsElement.isJsonArray -> {
                    // Predictions is an array - standard format
                    Log.d(TAG, "Processing predictions as array")
                    val predictionsArray = predictionsElement.asJsonArray
                    for (predictionElement in predictionsArray) {
                        try {
                            val prediction = gson.fromJson(predictionElement, RoboflowPrediction::class.java)
                            predictions.add(prediction)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing prediction from array: ${e.message}")
                        }
                    }
                }
                predictionsElement.isJsonObject -> {
                    // Predictions is an object - workflow format
                    Log.d(TAG, "Processing predictions as object")
                    val predictionsObject = predictionsElement.asJsonObject
                    
                    // Look for common patterns in workflow responses
                    when {
                        predictionsObject.has("predictions") -> {
                            // Nested predictions
                            val nestedPredictions = predictionsObject.get("predictions")
                            predictions.addAll(extractPredictionsFromJson(nestedPredictions))
                        }
                        predictionsObject.has("detections") -> {
                            // Alternative field name
                            val detections = predictionsObject.get("detections")
                            predictions.addAll(extractPredictionsFromJson(detections))
                        }
                        else -> {
                            // Try to parse the object itself as a single prediction
                            Log.d(TAG, "Trying to parse object as single prediction")
                            try {
                                val prediction = gson.fromJson(predictionsObject, RoboflowPrediction::class.java)
                                predictions.add(prediction)
                            } catch (e: Exception) {
                                Log.e(TAG, "Could not parse object as prediction: ${e.message}")
                                Log.d(TAG, "Object keys: ${predictionsObject.keySet()}")
                            }
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Predictions element is neither array nor object: ${predictionsElement.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting predictions from JSON: ${e.message}", e)
        }
        
        return predictions
    }
    
    private fun convertWorkflowPredictions(
        predictions: List<RoboflowPrediction>,
        originalWidth: Int,
        originalHeight: Int
    ): List<DamageDetection> {
        return predictions.map { prediction ->
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