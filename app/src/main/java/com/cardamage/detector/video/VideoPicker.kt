package com.cardamage.detector.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoPicker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "VideoPicker"
        private const val MAX_VIDEO_DURATION_MS = 300_000L // 5 minutes
        private const val MIN_VIDEO_DURATION_MS = 5_000L   // 5 seconds
    }

    suspend fun getVideoMetadata(uri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            
            val duration = durationStr?.toLongOrNull() ?: 0L
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0
            val rotation = rotationStr?.toIntOrNull() ?: 0
            
            retriever.release()
            
            VideoMetadata(
                uri = uri,
                duration = duration,
                width = width,
                height = height,
                rotation = rotation,
                fileSize = getVideoFileSize(uri)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video metadata for URI: $uri", e)
            null
        }
    }

    suspend fun validateVideo(uri: Uri): VideoValidationResult = withContext(Dispatchers.IO) {
        try {
            val metadata = getVideoMetadata(uri)
            
            if (metadata == null) {
                return@withContext VideoValidationResult(
                    isValid = false,
                    error = "Could not read video metadata. Video format may not be supported or file may be corrupted."
                )
            }
            
            if (metadata.duration < MIN_VIDEO_DURATION_MS) {
                return@withContext VideoValidationResult(
                    isValid = false,
                    error = "Video too short (minimum ${MIN_VIDEO_DURATION_MS / 1000} seconds)"
                )
            }
            
            if (metadata.duration > MAX_VIDEO_DURATION_MS) {
                return@withContext VideoValidationResult(
                    isValid = false,
                    error = "Video too long (maximum ${MAX_VIDEO_DURATION_MS / 1000} seconds)"
                )
            }
            
            if (metadata.width < 480 || metadata.height < 480) {
                return@withContext VideoValidationResult(
                    isValid = false,
                    error = "Video resolution too low (minimum 480x480)"
                )
            }
            
            VideoValidationResult(
                isValid = true,
                metadata = metadata
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating video: $uri", e)
            VideoValidationResult(
                isValid = false,
                error = "Error validating video: ${e.message}"
            )
        }
    }

    suspend fun extractVideoThumbnail(uri: Uri, timeUs: Long = 0L): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            Log.d(TAG, "Extracted thumbnail from video at time: ${timeUs}us")
            frame
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video thumbnail", e)
            null
        }
    }

    suspend fun getVideoFrameCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            // Estimate frame count based on duration and frame rate
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            
            val duration = durationStr?.toLongOrNull() ?: 0L
            val frameRate = frameRateStr?.toFloatOrNull() ?: 30f // Default to 30fps
            
            retriever.release()
            
            val estimatedFrames = ((duration / 1000f) * frameRate).toInt()
            Log.d(TAG, "Estimated frame count: $estimatedFrames for duration: ${duration}ms at ${frameRate}fps")
            
            estimatedFrames
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video frame count", e)
            0
        }
    }

    private fun getVideoFileSize(uri: Uri): Long {
        return try {
            val projection = arrayOf(MediaStore.Video.Media.SIZE)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                cursor.moveToFirst()
                cursor.getLong(sizeIndex)
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video file size", e)
            0L
        }
    }

    fun getMimeType(uri: Uri): String? {
        return try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MIME type", e)
            null
        }
    }

    fun isVideoMimeType(mimeType: String?): Boolean {
        return mimeType?.startsWith("video/") == true
    }
    
    fun isSupportedVideoFormat(mimeType: String?): Boolean {
        return when (mimeType) {
            "video/mp4",
            "video/3gpp",
            "video/avi",
            "video/quicktime",
            "video/x-msvideo" -> true
            else -> mimeType?.startsWith("video/") == true
        }
    }
    
    fun getSupportedFormatsMessage(): String {
        return "Supported formats: MP4, 3GP, AVI, MOV. Please ensure your video is in a supported format."
    }

    fun getVideoPath(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Video.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                cursor.moveToFirst()
                cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video path", e)
            null
        }
    }
}

data class VideoMetadata(
    val uri: Uri,
    val duration: Long,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val fileSize: Long = 0L
)

data class VideoValidationResult(
    val isValid: Boolean,
    val error: String? = null,
    val metadata: VideoMetadata? = null
)