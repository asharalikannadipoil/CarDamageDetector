package com.cardamage.detector.video

import android.net.Uri
import android.util.Log
import com.cardamage.detector.api.RoboflowClient
import com.cardamage.detector.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoProcessingService @Inject constructor(
    private val frameExtractor: VideoFrameExtractor,
    private val roboflowClient: RoboflowClient
) {
    
    companion object {
        private const val TAG = "VideoProcessingService"
    }

    suspend fun processVideo(
        videoUri: Uri,
        apiKey: String = "qkEc7KSs2JsjSn5XM2bB",
        onProgress: (VideoProcessingProgress) -> Unit = {},
        onFrameProcessed: (FrameAnalysisResult) -> Unit = {}
    ): VideoAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting video processing for: $videoUri")
            Log.d(TAG, "Video URI scheme: ${videoUri.scheme}, path: ${videoUri.path}")
            
            // Get video information
            val videoInfo = frameExtractor.getVideoInfo(videoUri)
            if (videoInfo == null) {
                Log.e(TAG, "Failed to extract video metadata for URI: $videoUri")
                throw Exception("Could not read video information. The video format may not be supported or the file may be corrupted. Please try a different video file.")
            }
            
            Log.d(TAG, "Video info - Duration: ${videoInfo.duration}ms, Size: ${videoInfo.width}x${videoInfo.height}, FPS: ${videoInfo.frameRate}")
            
            onProgress(VideoProcessingProgress.ExtractionStarted(videoInfo.duration))
            
            val frameResults = mutableListOf<FrameAnalysisResult>()
            var processedFrames = 0
            
            // Extract frames and process them
            val extractedFrames = frameExtractor.extractFrames(
                videoUri = videoUri,
                onFrameExtracted = { frameData ->
                    // Process frame immediately when extracted
                    this@withContext.launch {
                        try {
                            val frameResult = processFrame(frameData, apiKey)
                            frameResults.add(frameResult)
                            onFrameProcessed(frameResult)
                            
                            processedFrames++
                            onProgress(VideoProcessingProgress.FrameProcessed(
                                frameIndex = frameData.frameIndex,
                                totalFrames = processedFrames,
                                result = frameResult
                            ))
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing frame ${frameData.frameIndex}", e)
                            val errorFrame = FrameAnalysisResult(
                                frameIndex = frameData.frameIndex,
                                timestampMs = frameData.timestampMs,
                                detections = emptyList(),
                                extractionReason = frameData.extractionReason,
                                errorMessage = e.message
                            )
                            frameResults.add(errorFrame)
                            onFrameProcessed(errorFrame)
                        }
                    }
                },
                onProgress = { extractionProgress ->
                    onProgress(VideoProcessingProgress.ExtractionProgress(extractionProgress))
                }
            )
            
            // Wait for all frame processing to complete
            while (frameResults.size < extractedFrames.size) {
                delay(100)
            }
            
            // Sort results by frame index to maintain order
            frameResults.sortBy { it.frameIndex }
            
            val totalDetections = frameResults.sumOf { it.detections.size }
            val processingTime = System.currentTimeMillis() - startTime
            
            val result = VideoAnalysisResult(
                videoPath = videoUri.toString(),
                videoUri = videoUri,
                frames = frameResults,
                totalDetections = totalDetections,
                videoDuration = videoInfo.duration,
                processingTimeMs = processingTime
            )
            
            onProgress(VideoProcessingProgress.Completed(result))
            
            Log.d(TAG, "Video processing completed. Processed ${frameResults.size} frames with $totalDetections total detections in ${processingTime}ms")
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during video processing", e)
            val processingTime = System.currentTimeMillis() - startTime
            
            val errorResult = VideoAnalysisResult(
                videoPath = videoUri.toString(),
                videoUri = videoUri,
                frames = emptyList(),
                totalDetections = 0,
                videoDuration = 0L,
                processingTimeMs = processingTime,
                errorMessage = e.message
            )
            
            onProgress(VideoProcessingProgress.Error(e.message ?: "Unknown error"))
            errorResult
        }
    }

    private suspend fun processFrame(
        frameData: FrameData,
        apiKey: String
    ): FrameAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Processing frame ${frameData.frameIndex} at ${frameData.timestampMs}ms")
            
            // Use the standard damage detection method
            val analysisResult = roboflowClient.detectDamage(
                bitmap = frameData.bitmap,
                imagePath = "video_frame_${frameData.frameIndex}",
                apiKey = apiKey
            )
            
            val processingTime = System.currentTimeMillis() - startTime
            
            FrameAnalysisResult(
                frameIndex = frameData.frameIndex,
                timestampMs = frameData.timestampMs,
                detections = analysisResult.detections,
                processingTimeMs = processingTime,
                extractionReason = frameData.extractionReason,
                errorMessage = analysisResult.errorMessage
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame ${frameData.frameIndex}", e)
            val processingTime = System.currentTimeMillis() - startTime
            
            FrameAnalysisResult(
                frameIndex = frameData.frameIndex,
                timestampMs = frameData.timestampMs,
                detections = emptyList(),
                processingTimeMs = processingTime,
                extractionReason = frameData.extractionReason,
                errorMessage = e.message
            )
        } finally {
            // Clean up bitmap memory
            frameData.bitmap.recycle()
        }
    }

    fun createProgressFlow(
        videoUri: Uri,
        apiKey: String = "qkEc7KSs2JsjSn5XM2bB"
    ): Flow<VideoProcessingProgress> = callbackFlow {
        try {
            processVideo(
                videoUri = videoUri,
                apiKey = apiKey,
                onProgress = { progress ->
                    trySend(progress)
                }
            )
        } catch (e: Exception) {
            trySend(VideoProcessingProgress.Error(e.message ?: "Unknown error"))
        }
        close()
    }.flowOn(Dispatchers.IO)
}

sealed class VideoProcessingProgress {
    data class ExtractionStarted(val videoDuration: Long) : VideoProcessingProgress()
    data class ExtractionProgress(val progress: Float) : VideoProcessingProgress()
    data class FrameProcessed(
        val frameIndex: Int,
        val totalFrames: Int,
        val result: FrameAnalysisResult
    ) : VideoProcessingProgress()
    data class Completed(val result: VideoAnalysisResult) : VideoProcessingProgress()
    data class Error(val message: String) : VideoProcessingProgress()
}