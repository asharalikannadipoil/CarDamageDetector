package com.cardamage.detector.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Roboflow API service interface for car damage detection
 */
interface RoboflowApiService {
    
    @Multipart
    @POST("ayhan-gul-hgudf/car-damage-rlogo/1")
    suspend fun detectDamage(
        @Part image: MultipartBody.Part,
        @Query("api_key") apiKey: String,
        @Query("confidence") confidence: Float = 0.3f,
        @Query("overlap") overlap: Float = 0.45f
    ): Response<RoboflowDetectionResponse>
    
    @GET("ayhan-gul-hgudf/car-damage-rlogo")
    suspend fun getModelInfo(
        @Query("api_key") apiKey: String
    ): Response<RoboflowModelInfo>
}

/**
 * Roboflow detection response data classes
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