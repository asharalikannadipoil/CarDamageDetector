package com.cardamage.detector.video

import android.graphics.Bitmap
import android.util.Log

object BitmapUtils {
    private const val TAG = "BitmapUtils"
    private const val THUMBNAIL_SIZE = 150 // 150x150 pixels
    private const val PREVIEW_MAX_SIZE = 800 // Maximum size for preview images
    
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
     * Creates a memory-efficient preview bitmap with size constraints
     */
    fun createPreviewCopy(originalBitmap: Bitmap): Bitmap? {
        return try {
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            // If image is small enough, just copy it
            if (width <= PREVIEW_MAX_SIZE && height <= PREVIEW_MAX_SIZE) {
                return originalBitmap.copy(originalBitmap.config, false)
            }
            
            // Calculate scaling to fit within PREVIEW_MAX_SIZE
            val scale = minOf(
                PREVIEW_MAX_SIZE.toFloat() / width,
                PREVIEW_MAX_SIZE.toFloat() / height
            )
            
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            
            val preview = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            Log.d(TAG, "Created preview: ${width}x${height} -> ${newWidth}x${newHeight}")
            
            preview
        } catch (e: Exception) {
            Log.e(TAG, "Error creating preview bitmap", e)
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
    
    /**
     * Creates a memory-efficient thumbnail with optional memory manager integration
     */
    fun createManagedThumbnail(
        originalBitmap: Bitmap,
        memoryManager: MemoryManager? = null,
        key: String? = null,
        frameIndex: Int = -1
    ): Bitmap? {
        val thumbnail = createThumbnail(originalBitmap)
        
        if (thumbnail != null && memoryManager != null && key != null) {
            memoryManager.registerBitmap(
                key = key,
                bitmap = thumbnail,
                type = MemoryManager.BitmapType.THUMBNAIL,
                frameIndex = frameIndex
            )
        }
        
        return thumbnail
    }
    
    /**
     * Creates a memory-efficient preview with optional memory manager integration
     */
    fun createManagedPreview(
        originalBitmap: Bitmap,
        memoryManager: MemoryManager? = null,
        key: String? = null,
        frameIndex: Int = -1
    ): Bitmap? {
        val preview = createPreviewCopy(originalBitmap)
        
        if (preview != null && memoryManager != null && key != null) {
            memoryManager.registerBitmap(
                key = key,
                bitmap = preview,
                type = MemoryManager.BitmapType.PREVIEW,
                frameIndex = frameIndex
            )
        }
        
        return preview
    }
    
    /**
     * Calculate optimal bitmap sample size for memory efficiency
     */
    fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        
        while (width / sampleSize > maxSize || height / sampleSize > maxSize) {
            sampleSize *= 2
        }
        
        return sampleSize
    }
}