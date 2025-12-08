package com.cardamage.detector.api

import com.google.gson.JsonElement
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Roboflow API service interface for car damage detection workflow
 */
interface RoboflowApiService {
    
    @Headers("Content-Type: application/json")
    @POST("cardamagedetection-5okww/workflows/detect-count-and-visualize")
    suspend fun detectDamage(
        @Body request: RoboflowWorkflowRequest
    ): Response<RoboflowWorkflowResponse>
    
    @GET("finance-insitut/car-damage-detection-ku5hj/1")
    suspend fun getModelInfo(
        @Query("api_key") apiKey: String
    ): Response<RoboflowModelInfo>
}

/**
 * Roboflow workflow request and response data classes
 */
data class RoboflowWorkflowRequest(
    val api_key: String,
    val inputs: RoboflowInputs
)

data class RoboflowInputs(
    val image: RoboflowImageInput
)

data class RoboflowImageInput(
    val type: String, // "base64" or "url"
    val value: String // base64 string or URL
)

data class RoboflowWorkflowResponse(
    val outputs: List<RoboflowWorkflowOutput>? = null,
    val error: String? = null
)

data class RoboflowWorkflowOutput(
    val predictions: JsonElement? = null, // Flexible - can be object or array
    val visualization: String? = null,
    val count: Int? = null,
    val image: RoboflowWorkflowImage? = null
)

data class RoboflowWorkflowImage(
    val width: Int? = null,
    val height: Int? = null
)

/**
 * Legacy detection response data classes (kept for compatibility)
 */
data class RoboflowDetectionResponse(
    val predictions: List<RoboflowPrediction>,
    val image: RoboflowImageInfo,
    val inference_id: String? = null,
    val time: Double? = null
)

data class RoboflowPrediction(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val confidence: Double,
    val `class`: String,
    val class_id: Int,
    val detection_id: String? = null
)

data class RoboflowImageInfo(
    val width: Int,
    val height: Int
)

data class RoboflowModelInfo(
    val model: RoboflowModel,
    val dataset: RoboflowDataset? = null
)

data class RoboflowModel(
    val id: String,
    val name: String,
    val version: String,
    val classes: List<String>? = null
)

data class RoboflowDataset(
    val name: String,
    val classes: Map<String, Int>? = null,
    val images: Int? = null
)