package com.cardamage.detector.video

import android.graphics.Bitmap
import android.util.Log

object BitmapUtils {
    private const val TAG = "BitmapUtils"
    private const val THUMBNAIL_SIZE = 150 // 150x150 pixels
    
    /**
     * Creates a thumbnail bitmap maintaining aspect ratio
     */
    fun createThumbnail(originalBitmap: Bitmap): Bitmap? {
        return try {
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            // Calculate scaling factor to fit within THUMBNAIL_SIZE while maintaining aspect ratio
            val scale = minOf(
                THUMBNAIL_SIZE.toFloat() / width,
                THUMBNAIL_SIZE.toFloat() / height
            )
            
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            
            val thumbnail = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            Log.d(TAG, "Created thumbnail: ${width}x${height} -> ${newWidth}x${newHeight}")
            
            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Error creating thumbnail", e)
            null
        }
    }
    
    /**
     * Creates a copy of the bitmap for preview use
     */
    fun createPreviewCopy(originalBitmap: Bitmap): Bitmap? {
        return try {
            originalBitmap.copy(originalBitmap.config, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap copy", e)
            null
        }
    }
    
    /**
     * Safely recycles a bitmap if it's not null and not recycled
     */
    fun safeBitmapRecycle(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recycling bitmap", e)
        }
    }
    
    /**
     * Estimates the memory usage of a bitmap in bytes
     */
    fun getBitmapMemorySize(bitmap: Bitmap?): Long {
        return try {
            bitmap?.byteCount?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating bitmap memory size", e)
            0L
        }
    }
    
    /**
     * Checks if a bitmap is valid (not null, not recycled, has valid dimensions)
     */
    fun isValidBitmap(bitmap: Bitmap?): Boolean {
        return bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0
    }
}