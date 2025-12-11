package com.cardamage.detector.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class VideoFrameExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "VideoFrameExtractor"
        private const val FRAME_INTERVAL_MS = 30_000L // 30 seconds
        private const val SIDE_CHANGE_THRESHOLD = 0.3f // 30% pixel difference
        private const val MIN_FRAME_INTERVAL_MS = 5_000L // Minimum 5 seconds between frames
    }

    suspend fun extractFrames(
        videoUri: Uri,
        onFrameExtracted: suspend (FrameData) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): List<FrameData> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<FrameData>()
        var retriever: MediaMetadataRetriever? = null
        
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: return@withContext frames
            
            Log.d(TAG, "Extracting frames from video with duration: ${duration}ms")
            
            var currentTime = 0L
            var frameIndex = 0
            var previousFrame: Bitmap? = null
            
            while (currentTime < duration) {
                try {
                    val frame = retriever.getFrameAtTime(
                        currentTime * 1000, // Convert to microseconds
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    
                    if (frame != null) {
                        val shouldIncludeFrame = shouldIncludeFrame(frame, previousFrame, currentTime)
                        
                        if (shouldIncludeFrame) {
                            val frameData = FrameData(
                                bitmap = frame,
                                timestampMs = currentTime,
                                frameIndex = frameIndex++,
                                extractionReason = getExtractionReason(frame, previousFrame, currentTime)
                            )
                            
                            frames.add(frameData)
                            onFrameExtracted(frameData)
                            
                            Log.d(TAG, "Extracted frame at ${currentTime}ms (reason: ${frameData.extractionReason})")
                            
                            // Update previous frame for side-change detection
                            previousFrame?.recycle()
                            previousFrame = frame.copy(frame.config, false)
                        } else {
                            frame.recycle()
                        }
                    }
                    
                    // Update progress
                    onProgress(currentTime.toFloat() / duration)
                    
                    // Move to next time interval
                    currentTime += FRAME_INTERVAL_MS
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting frame at time: ${currentTime}ms", e)
                    currentTime += FRAME_INTERVAL_MS
                }
            }
            
            // Extract final frame if we haven't already
            if (frames.isEmpty() || frames.last().timestampMs < duration - FRAME_INTERVAL_MS) {
                try {
                    val finalFrame = retriever.getFrameAtTime(
                        (duration - 1000) * 1000, // 1 second before end
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    
                    if (finalFrame != null) {
                        val frameData = FrameData(
                            bitmap = finalFrame,
                            timestampMs = duration - 1000,
                            frameIndex = frameIndex,
                            extractionReason = FrameExtractionReason.TIME_INTERVAL
                        )
                        frames.add(frameData)
                        onFrameExtracted(frameData)
                        Log.d(TAG, "Extracted final frame at ${duration - 1000}ms")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting final frame", e)
                }
            }
            
            previousFrame?.recycle()
            onProgress(1.0f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during frame extraction", e)
        } finally {
            retriever?.release()
        }
        
        Log.d(TAG, "Frame extraction complete. Total frames: ${frames.size}")
        frames
    }

    private fun shouldIncludeFrame(
        currentFrame: Bitmap,
        previousFrame: Bitmap?,
        currentTime: Long
    ): Boolean {
        // Always include first frame
        if (previousFrame == null) {
            return true
        }
        
        // Check for significant visual change (vehicle side change)
        val visualDifference = calculateFrameDifference(currentFrame, previousFrame)
        if (visualDifference > SIDE_CHANGE_THRESHOLD) {
            Log.d(TAG, "Detected significant visual change: ${visualDifference}")
            return true
        }
        
        // Include frames at regular time intervals
        return currentTime % FRAME_INTERVAL_MS == 0L
    }

    private fun getExtractionReason(
        currentFrame: Bitmap,
        previousFrame: Bitmap?,
        currentTime: Long
    ): FrameExtractionReason {
        if (previousFrame == null) {
            return FrameExtractionReason.FIRST_FRAME
        }
        
        val visualDifference = calculateFrameDifference(currentFrame, previousFrame)
        if (visualDifference > SIDE_CHANGE_THRESHOLD) {
            return FrameExtractionReason.SIDE_CHANGE
        }
        
        return FrameExtractionReason.TIME_INTERVAL
    }

    private fun calculateFrameDifference(frame1: Bitmap, frame2: Bitmap): Float {
        if (frame1.width != frame2.width || frame1.height != frame2.height) {
            return 1.0f // Completely different if dimensions don't match
        }
        
        val width = frame1.width
        val height = frame1.height
        val totalPixels = width * height
        
        // Sample pixels for performance (every 10th pixel)
        val sampleStep = 10
        var differentPixels = 0
        var sampledPixels = 0
        
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel1 = frame1.getPixel(x, y)
                val pixel2 = frame2.getPixel(x, y)
                
                val rDiff = abs(((pixel1 shr 16) and 0xFF) - ((pixel2 shr 16) and 0xFF))
                val gDiff = abs(((pixel1 shr 8) and 0xFF) - ((pixel2 shr 8) and 0xFF))
                val bDiff = abs((pixel1 and 0xFF) - (pixel2 and 0xFF))
                
                // Consider pixels different if any channel differs by more than 30
                if (rDiff > 30 || gDiff > 30 || bDiff > 30) {
                    differentPixels++
                }
                sampledPixels++
            }
        }
        
        return if (sampledPixels > 0) {
            differentPixels.toFloat() / sampledPixels
        } else {
            0f
        }
    }

    suspend fun extractFrameAtTime(
        videoUri: Uri,
        timestampMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val frame = retriever.getFrameAtTime(
                timestampMs * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            Log.d(TAG, "Extracted single frame at ${timestampMs}ms")
            frame
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frame at time: ${timestampMs}ms", e)
            null
        } finally {
            retriever?.release()
        }
    }

    suspend fun getVideoInfo(videoUri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            
            VideoInfo(
                duration = durationStr?.toLongOrNull() ?: 0L,
                width = widthStr?.toIntOrNull() ?: 0,
                height = heightStr?.toIntOrNull() ?: 0,
                frameRate = frameRateStr?.toFloatOrNull() ?: 30f
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video info for URI: $videoUri", e)
            
            // Provide more specific error messages
            when {
                e.message?.contains("setDataSource failed") == true -> {
                    Log.e(TAG, "Video format may not be supported or file may be corrupted")
                }
                e.message?.contains("permission") == true -> {
                    Log.e(TAG, "Permission denied accessing video file")
                }
                else -> {
                    Log.e(TAG, "Unknown error processing video: ${e.message}")
                }
            }
            null
        } finally {
            retriever?.release()
        }
    }
}

data class FrameData(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val frameIndex: Int,
    val extractionReason: FrameExtractionReason
)

data class VideoInfo(
    val duration: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float
)

enum class FrameExtractionReason {
    FIRST_FRAME,
    TIME_INTERVAL,
    SIDE_CHANGE
}