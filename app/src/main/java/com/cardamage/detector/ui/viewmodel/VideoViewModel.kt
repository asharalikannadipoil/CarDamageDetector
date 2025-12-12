package com.cardamage.detector.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardamage.detector.data.model.*
import com.cardamage.detector.video.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoProcessingService: VideoProcessingService,
    private val videoPicker: VideoPicker,
    private val memoryManager: MemoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _processingProgress = MutableStateFlow<VideoProcessingProgress?>(null)
    val processingProgress: StateFlow<VideoProcessingProgress?> = _processingProgress.asStateFlow()

    private val _frameResults = MutableStateFlow<List<FrameAnalysisResult>>(emptyList())
    val frameResults: StateFlow<List<FrameAnalysisResult>> = _frameResults.asStateFlow()

    private var processingJob: Job? = null

    fun processVideo(videoUri: Uri) {
        if (processingJob?.isActive == true) {
            Log.d("VideoViewModel", "Video processing already in progress, skipping new request")
            return // Already processing
        }

        processingJob = viewModelScope.launch {
            try {
                Log.d("VideoViewModel", "Starting video processing for URI: $videoUri")
                _uiState.value = _uiState.value.copy(
                    currentVideoUri = videoUri,
                    isLoading = true,
                    error = null
                )
                _processingProgress.value = null
                _frameResults.value = emptyList()

                // Validate video first
                val validation = videoPicker.validateVideo(videoUri)
                if (!validation.isValid) {
                    val errorMessage = "${validation.error} ${videoPicker.getSupportedFormatsMessage()}"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                    return@launch
                }

                // Start processing
                val result = videoProcessingService.processVideo(
                    videoUri = videoUri,
                    onProgress = { progress ->
                        _processingProgress.value = progress
                        
                        when (progress) {
                            is VideoProcessingProgress.Completed -> {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    analysisResult = progress.result
                                )
                            }
                            is VideoProcessingProgress.Error -> {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = progress.message
                                )
                            }
                            else -> {
                                // Keep loading state for other progress types
                            }
                        }
                    },
                    onFrameProcessed = { frameResult ->
                        val currentFrames = _frameResults.value.toMutableList()
                        currentFrames.add(frameResult)
                        _frameResults.value = currentFrames.sortedBy { it.frameIndex }
                    }
                )

                // Final state update
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    analysisResult = result
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to process video: ${e.message}"
                )
                _processingProgress.value = VideoProcessingProgress.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
        
        // Clean up memory when stopping processing
        memoryManager.cleanupBitmapType(MemoryManager.BitmapType.THUMBNAIL)
    }
    
    /**
     * Get memory manager for UI monitoring
     */
    fun getMemoryManager(): MemoryManager = memoryManager
    
    /**
     * Perform manual memory cleanup
     */
    fun cleanupMemory() {
        viewModelScope.launch {
            try {
                // Prioritize cleanup of thumbnails and older frames
                memoryManager.cleanupBitmapType(MemoryManager.BitmapType.THUMBNAIL)
                
                // If still high memory usage, cleanup some previews
                if (memoryManager.isMemoryUsageCritical()) {
                    val frameCount = _frameResults.value.size
                    if (frameCount > 10) {
                        // Clean up frames except the last 5
                        for (i in 0 until frameCount - 5) {
                            memoryManager.cleanupFrame(i)
                        }
                    }
                }
                
                Log.d("VideoViewModel", "Manual memory cleanup completed. Usage: ${memoryManager.getCurrentMemoryUsageMB()}MB")
                
            } catch (e: Exception) {
                Log.e("VideoViewModel", "Error during memory cleanup", e)
            }
        }
    }

    fun resetAnalysis() {
        processingJob?.cancel()
        _uiState.value = VideoUiState()
        _processingProgress.value = null
        _frameResults.value = emptyList()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getFramesSummary(): VideoFramesSummary {
        val frames = _frameResults.value
        val totalFrames = frames.size
        val framesWithDetections = frames.count { it.hasDetections() }
        val totalDetections = frames.sumOf { it.detections.size }
        val averageConfidence = frames
            .flatMap { it.detections }
            .map { it.confidence }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0

        val detectionsByType = frames
            .flatMap { it.detections }
            .groupBy { it.type }
            .mapValues { it.value.size }

        return VideoFramesSummary(
            totalFrames = totalFrames,
            framesWithDetections = framesWithDetections,
            totalDetections = totalDetections,
            averageConfidence = averageConfidence.toFloat(),
            detectionsByType = detectionsByType
        )
    }

    fun getTimelineData(): List<FrameTimelineItem> {
        return _frameResults.value.map { frame ->
            FrameTimelineItem(
                frameIndex = frame.frameIndex,
                timestampMs = frame.timestampMs,
                detectionCount = frame.detections.size,
                hasHighConfidenceDetections = frame.getHighConfidenceDetections().isNotEmpty(),
                extractionReason = frame.extractionReason
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
    }
}

data class VideoUiState(
    val currentVideoUri: Uri? = null,
    val isLoading: Boolean = false,
    val analysisResult: VideoAnalysisResult? = null,
    val error: String? = null
)

data class VideoFramesSummary(
    val totalFrames: Int,
    val framesWithDetections: Int,
    val totalDetections: Int,
    val averageConfidence: Float,
    val detectionsByType: Map<DamageType, Int>
)

data class FrameTimelineItem(
    val frameIndex: Int,
    val timestampMs: Long,
    val detectionCount: Int,
    val hasHighConfidenceDetections: Boolean,
    val extractionReason: FrameExtractionReason
)