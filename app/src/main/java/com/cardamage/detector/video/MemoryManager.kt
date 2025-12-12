package com.cardamage.detector.video

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor() {
    
    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_MEMORY_MB = 100 // Maximum memory usage for bitmaps (100MB)
        private const val CLEANUP_THRESHOLD_MB = 80 // Start cleanup when reaching 80MB
    }
    
    private val activeBitmaps = ConcurrentHashMap<String, BitmapReference>()
    private val currentMemoryUsage = AtomicLong(0)
    private val maxMemoryBytes = MAX_MEMORY_MB * 1024 * 1024L
    private val cleanupThresholdBytes = CLEANUP_THRESHOLD_MB * 1024 * 1024L
    
    data class BitmapReference(
        val bitmap: Bitmap,
        val type: BitmapType,
        val createdAt: Long,
        val lastAccessedAt: Long,
        val frameIndex: Int = -1
    )
    
    enum class BitmapType {
        ORIGINAL_FRAME,
        THUMBNAIL,
        PREVIEW
    }
    
    /**
     * Register a bitmap for memory tracking
     */
    fun registerBitmap(
        key: String,
        bitmap: Bitmap,
        type: BitmapType,
        frameIndex: Int = -1
    ) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Attempted to register recycled bitmap: $key")
            return
        }
        
        val size = BitmapUtils.getBitmapMemorySize(bitmap)
        val reference = BitmapReference(
            bitmap = bitmap,
            type = type,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            frameIndex = frameIndex
        )
        
        activeBitmaps[key] = reference
        currentMemoryUsage.addAndGet(size)
        
        Log.d(TAG, "Registered bitmap: $key (${type.name}) - ${size / 1024}KB. Total: ${getCurrentMemoryUsageMB()}MB")
        
        // Check if we need to cleanup
        if (currentMemoryUsage.get() > cleanupThresholdBytes) {
            performMemoryCleanup()
        }
    }
    
    /**
     * Unregister a bitmap and recycle it if needed
     */
    fun unregisterBitmap(key: String): Boolean {
        val reference = activeBitmaps.remove(key) ?: return false
        
        val size = BitmapUtils.getBitmapMemorySize(reference.bitmap)
        currentMemoryUsage.addAndGet(-size)
        
        if (!reference.bitmap.isRecycled) {
            reference.bitmap.recycle()
            Log.d(TAG, "Recycled bitmap: $key (${reference.type.name}) - ${size / 1024}KB")
        }
        
        Log.d(TAG, "Unregistered bitmap: $key. Total: ${getCurrentMemoryUsageMB()}MB")
        return true
    }
    
    /**
     * Get a bitmap reference and update last accessed time
     */
    fun getBitmap(key: String): Bitmap? {
        val reference = activeBitmaps[key] ?: return null
        
        if (reference.bitmap.isRecycled) {
            Log.w(TAG, "Found recycled bitmap: $key, removing from tracking")
            activeBitmaps.remove(key)
            return null
        }
        
        // Update last accessed time
        val updatedReference = reference.copy(lastAccessedAt = System.currentTimeMillis())
        activeBitmaps[key] = updatedReference
        
        return reference.bitmap
    }
    
    /**
     * Check if a bitmap exists and is valid
     */
    fun hasBitmap(key: String): Boolean {
        val reference = activeBitmaps[key] ?: return false
        return BitmapUtils.isValidBitmap(reference.bitmap)
    }
    
    /**
     * Perform memory cleanup by removing old or low-priority bitmaps
     */
    private fun performMemoryCleanup() {
        Log.d(TAG, "Starting memory cleanup. Current usage: ${getCurrentMemoryUsageMB()}MB")
        
        val currentTime = System.currentTimeMillis()
        val cleanupCandidates = activeBitmaps.values
            .filter { !it.bitmap.isRecycled }
            .sortedWith(compareBy<BitmapReference> { it.type.priority() }
                .thenBy { currentTime - it.lastAccessedAt })
        
        var freedMemory = 0L
        val targetReduction = (currentMemoryUsage.get() - cleanupThresholdBytes * 0.7).toLong()
        
        for (reference in cleanupCandidates) {
            if (freedMemory >= targetReduction) break
            
            val key = activeBitmaps.entries.find { it.value === reference }?.key ?: continue
            val size = BitmapUtils.getBitmapMemorySize(reference.bitmap)
            
            if (unregisterBitmap(key)) {
                freedMemory += size
                Log.d(TAG, "Cleaned up bitmap: $key (${reference.type.name}) - ${size / 1024}KB")
            }
        }
        
        Log.d(TAG, "Memory cleanup completed. Freed: ${freedMemory / 1024}KB. New usage: ${getCurrentMemoryUsageMB()}MB")
    }
    
    /**
     * Force cleanup of all bitmaps for a specific frame
     */
    fun cleanupFrame(frameIndex: Int) {
        val frameKeys = activeBitmaps.entries
            .filter { it.value.frameIndex == frameIndex }
            .map { it.key }
        
        frameKeys.forEach { key ->
            unregisterBitmap(key)
        }
        
        Log.d(TAG, "Cleaned up ${frameKeys.size} bitmaps for frame $frameIndex")
    }
    
    /**
     * Force cleanup of specific bitmap type
     */
    fun cleanupBitmapType(type: BitmapType) {
        val typeKeys = activeBitmaps.entries
            .filter { it.value.type == type }
            .map { it.key }
        
        typeKeys.forEach { key ->
            unregisterBitmap(key)
        }
        
        Log.d(TAG, "Cleaned up ${typeKeys.size} bitmaps of type ${type.name}")
    }
    
    /**
     * Emergency cleanup - remove all bitmaps
     */
    fun emergencyCleanup() {
        Log.w(TAG, "Performing emergency memory cleanup")
        
        val allKeys = activeBitmaps.keys.toList()
        allKeys.forEach { key ->
            unregisterBitmap(key)
        }
        
        Log.d(TAG, "Emergency cleanup completed. Cleaned up ${allKeys.size} bitmaps")
    }
    
    /**
     * Get current memory usage in MB
     */
    fun getCurrentMemoryUsageMB(): Float {
        return currentMemoryUsage.get() / (1024f * 1024f)
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val bitmapsByType = activeBitmaps.values.groupBy { it.type }
        
        return MemoryStats(
            totalBitmaps = activeBitmaps.size,
            totalMemoryMB = getCurrentMemoryUsageMB(),
            maxMemoryMB = MAX_MEMORY_MB.toFloat(),
            thumbnails = bitmapsByType[BitmapType.THUMBNAIL]?.size ?: 0,
            previews = bitmapsByType[BitmapType.PREVIEW]?.size ?: 0,
            originals = bitmapsByType[BitmapType.ORIGINAL_FRAME]?.size ?: 0
        )
    }
    
    /**
     * Check if memory usage is critical
     */
    fun isMemoryUsageCritical(): Boolean {
        return currentMemoryUsage.get() > maxMemoryBytes * 0.9
    }
    
    /**
     * Get priority for cleanup (lower number = higher priority for cleanup)
     */
    private fun BitmapType.priority(): Int {
        return when (this) {
            BitmapType.ORIGINAL_FRAME -> 3 // Keep originals longest
            BitmapType.PREVIEW -> 2      // Keep previews moderate time
            BitmapType.THUMBNAIL -> 1    // Clean thumbnails first
        }
    }
    
    data class MemoryStats(
        val totalBitmaps: Int,
        val totalMemoryMB: Float,
        val maxMemoryMB: Float,
        val thumbnails: Int,
        val previews: Int,
        val originals: Int
    ) {
        val usagePercentage: Float = (totalMemoryMB / maxMemoryMB) * 100f
        val isNearLimit: Boolean = usagePercentage > 80f
    }
}