package com.cardamage.detector.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImagePicker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ImagePicker"
        private const val MAX_IMAGE_SIZE = 2048
    }

    suspend fun loadImageFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                return@withContext null
            }

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                return@withContext null
            }

            val rotatedBitmap = handleImageRotation(bitmap, uri)
            val resizedBitmap = resizeImageIfNeeded(rotatedBitmap)
            
            if (rotatedBitmap != bitmap && rotatedBitmap != resizedBitmap) {
                rotatedBitmap.recycle()
            }
            if (bitmap != resizedBitmap && bitmap != rotatedBitmap) {
                bitmap.recycle()
            }

            Log.d(TAG, "Image loaded successfully from URI: $uri")
            resizedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error loading image from URI: $uri", e)
            null
        }
    }

    private fun handleImageRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            ) ?: ExifInterface.ORIENTATION_UNDEFINED

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read EXIF data, using original orientation", e)
            bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun resizeImageIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }

        val ratio = minOf(
            MAX_IMAGE_SIZE.toFloat() / width,
            MAX_IMAGE_SIZE.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        Log.d(TAG, "Resizing image from ${width}x${height} to ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun validateImage(bitmap: Bitmap?): Boolean {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null")
            return false
        }

        if (bitmap.isRecycled) {
            Log.e(TAG, "Bitmap is recycled")
            return false
        }

        if (bitmap.width < 224 || bitmap.height < 224) {
            Log.e(TAG, "Image too small: ${bitmap.width}x${bitmap.height}")
            return false
        }

        Log.d(TAG, "Image validation passed: ${bitmap.width}x${bitmap.height}")
        return true
    }

    fun getImageDimensions(uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            if (options.outWidth != -1 && options.outHeight != -1) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image dimensions", e)
            null
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

    fun isImageMimeType(mimeType: String?): Boolean {
        return mimeType?.startsWith("image/") == true
    }

    fun getImagePath(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image path", e)
            null
        }
    }
}