package com.cardamage.detector.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var activeRecording: Recording? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    enum class CaptureMode {
        PHOTO, VIDEO
    }
    
    private var currentCaptureMode = CaptureMode.PHOTO
    
    fun initializeCamera(
        previewView: androidx.camera.view.PreviewView,
        captureMode: CaptureMode = CaptureMode.PHOTO,
        onError: (String) -> Unit = {}
    ) {
        currentCaptureMode = captureMode
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                startCamera(previewView)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                onError("Failed to initialize camera: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProvider = cameraProvider ?: return

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val useCases = mutableListOf<UseCase>(preview!!)
        
        when (currentCaptureMode) {
            CaptureMode.PHOTO -> {
                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                useCases.add(imageCapture!!)
            }
            CaptureMode.VIDEO -> {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                useCases.add(videoCapture!!)
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Camera binding failed", exc)
        }
    }

    fun capturePhoto(
        outputDirectory: File,
        onImageCaptured: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError("Camera not initialized")
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        
        val photoFile = File(outputDirectory, "IMG_$name.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d(TAG, "Photo capture succeeded: $savedUri")
                    onImageCaptured(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    onError("Photo capture failed: ${exception.message}")
                }
            }
        )
    }

    fun toggleFlash() {
        camera?.let { camera ->
            val currentFlashMode = camera.cameraInfo.torchState.value
            val newFlashMode = when (currentFlashMode) {
                TorchState.OFF -> TorchState.ON
                TorchState.ON -> TorchState.OFF
                else -> TorchState.OFF
            }
            camera.cameraControl.enableTorch(newFlashMode == TorchState.ON)
        }
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun getZoomState() = camera?.cameraInfo?.zoomState

    fun getTorchState() = camera?.cameraInfo?.torchState

    fun hasFlashUnit(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    fun startVideoRecording(
        outputDirectory: File,
        onVideoRecorded: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val videoCapture = videoCapture ?: run {
            onError("Video capture not initialized")
            return
        }

        if (activeRecording != null) {
            onError("Video recording already in progress")
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        
        val videoFile = File(outputDirectory, "VID_$name.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Video recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val savedUri = Uri.fromFile(videoFile)
                            Log.d(TAG, "Video capture succeeded: $savedUri")
                            onVideoRecorded(savedUri)
                        } else {
                            Log.e(TAG, "Video capture failed", recordEvent.cause)
                            onError("Video capture failed: ${recordEvent.cause?.message}")
                        }
                        activeRecording = null
                    }
                }
            }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isRecording(): Boolean {
        return activeRecording != null
    }

    fun setCaptureMode(mode: CaptureMode, previewView: androidx.camera.view.PreviewView) {
        if (currentCaptureMode != mode) {
            currentCaptureMode = mode
            startCamera(previewView)
        }
    }

    fun shutdown() {
        activeRecording?.stop()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}