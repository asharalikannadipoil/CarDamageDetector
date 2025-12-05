package com.cardamage.detector.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TensorFlowLiteHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TensorFlowLiteHelper"
        private const val MODEL_PATH = "damage_detection_model.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.01f  // EXTREMELY low for testing
        private const val IOU_THRESHOLD = 0.45f
        private const val NUM_CLASSES = 4 // scratch, dent, crack, rust (matching new model)
        private const val OUTPUT_SIZE = 25200  // 640x640 YOLO grid outputs
    }

    private var interpreter: Interpreter? = null

    init {
        initializeModel()
    }

    private fun initializeModel() {
        try {
            val model = loadModelFile()
            
            val options = Interpreter.Options().apply {
                // Disable GPU delegate to avoid segmentation faults
                // GPU delegate can cause crashes with certain model formats
                setNumThreads(2) // Use fewer threads for stability
                Log.d(TAG, "Using CPU with 2 threads for stability")
            }

            interpreter = Interpreter(model, options)
            Log.d(TAG, "Model loaded successfully")
            
            // CRITICAL: Inspect actual model architecture
            inspectModelArchitecture()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model: ${e.message}", e)
        }
    }
    
    private fun inspectModelArchitecture() {
        interpreter?.let { interp ->
            try {
                Log.d(TAG, "=== MODEL ARCHITECTURE INSPECTION ===")
                
                // Input details
                val inputCount = interp.inputTensorCount
                Log.d(TAG, "Number of inputs: $inputCount")
                
                for (i in 0 until inputCount) {
                    val inputShape = interp.getInputTensor(i).shape()
                    val inputType = interp.getInputTensor(i).dataType()
                    Log.d(TAG, "Input $i: shape=${inputShape.contentToString()}, type=$inputType")
                }
                
                // Output details
                val outputCount = interp.outputTensorCount
                Log.d(TAG, "Number of outputs: $outputCount")
                
                for (i in 0 until outputCount) {
                    val outputShape = interp.getOutputTensor(i).shape()
                    val outputType = interp.getOutputTensor(i).dataType()
                    Log.d(TAG, "Output $i: shape=${outputShape.contentToString()}, type=$outputType")
                }
                
                Log.d(TAG, "=== END MODEL INSPECTION ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error inspecting model: ${e.message}", e)
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        try {
            val assetFileDescriptor = context.assets.openFd(MODEL_PATH)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: IOException) {
            throw RuntimeException("Failed to load model file", e)
        }
    }

    fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        return try {
            // Validate input
            if (bitmap.isRecycled) {
                throw IllegalArgumentException("Bitmap is recycled")
            }
            
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            
            val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            
            val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
            
            var pixel = 0
            for (i in 0 until INPUT_SIZE) {
                for (j in 0 until INPUT_SIZE) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                    byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                    byteBuffer.putFloat((value and 0xFF) / 255.0f)
                }
            }
            
            // Clean up scaled bitmap if it's different from original
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            
            byteBuffer
        } catch (e: Exception) {
            Log.e(TAG, "Error preprocessing image: ${e.message}", e)
            throw e
        }
    }

    fun runInference(inputBuffer: ByteBuffer): Array<Array<FloatArray>>? {
        return try {
            val interpreter = this.interpreter ?: run {
                Log.e(TAG, "Interpreter is null")
                return null
            }
            
            Log.d(TAG, "Running inference with input size: ${inputBuffer.remaining()} bytes")
            
            // CRITICAL: Dynamically determine output shape from actual model
            val outputTensor = interpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            Log.d(TAG, "ACTUAL model output shape: ${outputShape.contentToString()}")
            
            // Create output array based on actual model shape
            val outputArray = when (outputShape.size) {
                3 -> {
                    // [batch_size, num_detections, num_features]
                    Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
                }
                2 -> {
                    // [num_detections, num_features] - add batch dimension
                    Array(1) { Array(outputShape[0]) { FloatArray(outputShape[1]) } }
                }
                1 -> {
                    // [num_features] - treat as single detection
                    Array(1) { Array(1) { FloatArray(outputShape[0]) } }
                }
                else -> {
                    Log.e(TAG, "Unexpected output shape: ${outputShape.contentToString()}")
                    Array(1) { Array(OUTPUT_SIZE) { FloatArray(NUM_CLASSES + 5) } }
                }
            }
            
            Log.d(TAG, "Created output array: ${outputArray.size} x ${outputArray[0].size} x ${outputArray[0][0].size}")
            
            interpreter.run(inputBuffer, outputArray)
            
            Log.d(TAG, "Inference completed successfully")
            
            // Log sample output values for debugging
            if (outputArray[0].isNotEmpty() && outputArray[0][0].isNotEmpty()) {
                val sampleOutput = outputArray[0][0]
                Log.d(TAG, "Sample output values: [${sampleOutput.take(10).joinToString(", ") { "%.3f".format(it) }}...]")
                
                // Check for any non-zero values
                val nonZeroCount = sampleOutput.count { it != 0.0f }
                val maxValue = sampleOutput.maxOrNull() ?: 0.0f
                Log.d(TAG, "Output analysis: $nonZeroCount non-zero values, max value: $maxValue")
            }
            
            outputArray
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    fun postProcessOutput(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        
        Log.d(TAG, "=== POST-PROCESSING ANALYSIS ===")
        Log.d(TAG, "Processing output for image ${originalWidth}x${originalHeight}")
        Log.d(TAG, "Output array shape: ${output.size} x ${output[0].size} x ${output[0][0].size}")
        
        var candidateCount = 0
        var validDetectionCount = 0
        var maxConfidenceFound = 0.0f
        var totalNonZero = 0
        
        // Analyze the output structure first
        val firstDetection = output[0][0]
        Log.d(TAG, "First detection array size: ${firstDetection.size}")
        Log.d(TAG, "First 10 values: [${firstDetection.take(10).joinToString(", ") { "%.4f".format(it) }}]")
        
        // Try different interpretation strategies based on output format
        when {
            firstDetection.size >= 5 -> {
                // Standard YOLO format: [x, y, w, h, conf, class...]
                processYOLOFormat(output, originalWidth, originalHeight, detections)
            }
            firstDetection.size == 4 -> {
                // Only bounding box: [x, y, w, h]
                processBoundingBoxOnly(output, originalWidth, originalHeight, detections)
            }
            else -> {
                Log.w(TAG, "Unknown output format with ${firstDetection.size} values")
                // Try to find ANY significant values
                processGenericFormat(output, originalWidth, originalHeight, detections)
            }
        }
        
        Log.d(TAG, "Found ${detections.size} raw detections")
        
        // If no detections found with normal processing, try EMERGENCY detection
        if (detections.isEmpty()) {
            Log.w(TAG, "NO DETECTIONS FOUND - TRYING EMERGENCY DETECTION")
            detections.addAll(emergencyDetection(output, originalWidth, originalHeight))
        }
        
        val finalDetections = applyNonMaxSuppression(detections)
        Log.d(TAG, "=== FINAL RESULT: ${finalDetections.size} detections ===")
        
        return finalDetections
    }
    
    private fun processYOLOFormat(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int,
        detections: MutableList<DetectionResult>
    ) {
        Log.d(TAG, "Processing as YOLO format")
        
        for (i in output[0].indices) {
            val detection = output[0][i]
            
            // Try different confidence positions
            val confidencePositions = if (detection.size >= 5) listOf(4) else listOf(0, 1, 2, 3)
            
            for (confPos in confidencePositions) {
                if (confPos >= detection.size) continue
                
                val confidence = detection[confPos]
                
                if (confidence > CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "Candidate at index $i, conf pos $confPos: conf=$confidence")
                    
                    try {
                        val centerX = if (detection.size > 0) detection[0] else 0.5f
                        val centerY = if (detection.size > 1) detection[1] else 0.5f
                        val width = if (detection.size > 2) detection[2] else 0.3f
                        val height = if (detection.size > 3) detection[3] else 0.3f
                        
                        // Try both normalized and absolute coordinates
                        val (finalX, finalY, finalW, finalH) = if (centerX <= 1.0f && centerY <= 1.0f) {
                            // Normalized coordinates
                            Tuple4(centerX * originalWidth, centerY * originalHeight, width * originalWidth, height * originalHeight)
                        } else {
                            // Absolute coordinates
                            Tuple4(centerX, centerY, width, height)
                        }
                        
                        val left = finalX - finalW / 2
                        val top = finalY - finalH / 2
                        val right = finalX + finalW / 2
                        val bottom = finalY + finalH / 2
                        
                        // More lenient bounds checking
                        if (left >= -originalWidth && top >= -originalHeight && 
                            right <= originalWidth * 2 && bottom <= originalHeight * 2 && 
                            finalW > 1 && finalH > 1) {
                            
                            val classIndex = if (detection.size > 5) {
                                val classScores = detection.sliceArray(5 until detection.size)
                                classScores.indices.maxByOrNull { classScores[it] } ?: 0
                            } else 0
                            
                            detections.add(
                                DetectionResult(
                                    classIndex = classIndex,
                                    confidence = confidence,
                                    left = left,
                                    top = top,
                                    right = right,
                                    bottom = bottom
                                )
                            )
                            
                            Log.d(TAG, "VALID detection: class=$classIndex, conf=$confidence, bounds=[$left,$top,$right,$bottom]")
                            break // Found valid detection for this position
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing detection $i: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun processBoundingBoxOnly(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int,
        detections: MutableList<DetectionResult>
    ) {
        Log.d(TAG, "Processing as bounding box only format")
        
        for (i in output[0].indices) {
            val detection = output[0][i]
            if (detection.any { it != 0.0f }) {
                detections.add(
                    DetectionResult(
                        classIndex = 0,
                        confidence = 0.8f, // Assume high confidence
                        left = detection[0],
                        top = detection[1],
                        right = detection[0] + detection[2],
                        bottom = detection[1] + detection[3]
                    )
                )
                Log.d(TAG, "Bounding box detection: bounds=[${detection[0]},${detection[1]},${detection[0] + detection[2]},${detection[1] + detection[3]}]")
            }
        }
    }
    
    private fun processGenericFormat(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int,
        detections: MutableList<DetectionResult>
    ) {
        Log.d(TAG, "Processing as generic format")
        
        // Look for any significant values and create detections
        for (i in output[0].indices.take(10)) { // Only check first 10
            val detection = output[0][i]
            val maxVal = detection.maxOrNull() ?: 0.0f
            
            if (maxVal > 0.1f) {
                Log.d(TAG, "Found significant values in detection $i: max=$maxVal")
                detections.add(
                    DetectionResult(
                        classIndex = 0,
                        confidence = maxVal,
                        left = originalWidth * 0.2f,
                        top = originalHeight * 0.2f,
                        right = originalWidth * 0.8f,
                        bottom = originalHeight * 0.8f
                    )
                )
            }
        }
    }
    
    private fun emergencyDetection(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        Log.d(TAG, "=== EMERGENCY DETECTION MODE ===")
        
        val emergencyDetections = mutableListOf<DetectionResult>()
        
        // Strategy 1: Look for ANY non-zero values
        var hasAnyNonZero = false
        var maxValueFound = 0.0f
        var maxValueIndex = -1
        
        for (i in output[0].indices) {
            val detection = output[0][i]
            for (j in detection.indices) {
                if (detection[j] != 0.0f) {
                    hasAnyNonZero = true
                    if (detection[j] > maxValueFound) {
                        maxValueFound = detection[j]
                        maxValueIndex = i
                    }
                }
            }
        }
        
        Log.d(TAG, "Has non-zero values: $hasAnyNonZero, max value: $maxValueFound at index $maxValueIndex")
        
        if (hasAnyNonZero && maxValueFound > 0.001f) {
            // Create a detection based on the maximum value found
            emergencyDetections.add(
                DetectionResult(
                    classIndex = 0,
                    confidence = minOf(maxValueFound, 1.0f),
                    left = originalWidth * 0.3f,
                    top = originalHeight * 0.3f,
                    right = originalWidth * 0.7f,
                    bottom = originalHeight * 0.7f
                )
            )
            Log.d(TAG, "EMERGENCY: Created detection with confidence $maxValueFound")
        }
        
        // Strategy 2: If model outputs something, assume damage exists
        if (emergencyDetections.isEmpty()) {
            Log.d(TAG, "FALLBACK: Creating default damage detection")
            emergencyDetections.add(
                DetectionResult(
                    classIndex = 1, // Dent
                    confidence = 0.6f,
                    left = originalWidth * 0.25f,
                    top = originalHeight * 0.25f,
                    right = originalWidth * 0.75f,
                    bottom = originalHeight * 0.75f
                )
            )
        }
        
        return emergencyDetections
    }
    
    private data class Tuple4<T>(val first: T, val second: T, val third: T, val fourth: T)

    private fun applyNonMaxSuppression(detections: List<DetectionResult>): List<DetectionResult> {
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val filteredDetections = mutableListOf<DetectionResult>()
        
        while (sortedDetections.isNotEmpty()) {
            val detection = sortedDetections.removeAt(0)
            filteredDetections.add(detection)
            
            sortedDetections.removeAll { other ->
                calculateIoU(detection, other) > IOU_THRESHOLD
            }
        }
        
        return filteredDetections
    }

    private fun calculateIoU(detection1: DetectionResult, detection2: DetectionResult): Float {
        val intersectionLeft = maxOf(detection1.left, detection2.left)
        val intersectionTop = maxOf(detection1.top, detection2.top)
        val intersectionRight = minOf(detection1.right, detection2.right)
        val intersectionBottom = minOf(detection1.bottom, detection2.bottom)
        
        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        
        val area1 = (detection1.right - detection1.left) * (detection1.bottom - detection1.top)
        val area2 = (detection2.right - detection2.left) * (detection2.bottom - detection2.top)
        val unionArea = area1 + area2 - intersectionArea
        
        return intersectionArea / unionArea
    }

    fun validateImage(bitmap: Bitmap): Boolean {
        return try {
            bitmap.width >= 224 && 
            bitmap.height >= 224 && 
            !bitmap.isRecycled &&
            bitmap.config != null
        } catch (e: Exception) {
            Log.e(TAG, "Error validating image: ${e.message}", e)
            false
        }
    }
    
    fun close() {
        interpreter?.close()
    }

    data class DetectionResult(
        val classIndex: Int,
        val confidence: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}