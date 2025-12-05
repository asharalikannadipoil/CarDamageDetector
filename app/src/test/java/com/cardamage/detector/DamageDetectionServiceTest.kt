package com.cardamage.detector

import android.graphics.Bitmap
import com.cardamage.detector.data.model.*
import com.cardamage.detector.ml.DamageDetectionService
import com.cardamage.detector.ml.TensorFlowLiteHelper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(MockitoJUnitRunner::class)
class DamageDetectionServiceTest {

    @Mock
    private lateinit var tensorFlowHelper: TensorFlowLiteHelper

    private lateinit var damageDetectionService: DamageDetectionService

    @Before
    fun setup() {
        damageDetectionService = DamageDetectionService(tensorFlowHelper)
    }

    @Test
    fun `detectDamage should return result with empty detections when no damage found`() = runTest {
        // Given
        val mockBitmap = mock<Bitmap> {
            on { width } doReturn 640
            on { height } doReturn 480
        }
        val imagePath = "/test/path/image.jpg"
        
        whenever(tensorFlowHelper.preprocessImage(any())).thenReturn(mock())
        whenever(tensorFlowHelper.runInference(any())).thenReturn(arrayOf(Array(25200) { FloatArray(9) }))
        whenever(tensorFlowHelper.postProcessOutput(any(), any(), any())).thenReturn(emptyList())

        // When
        val result = damageDetectionService.detectDamage(mockBitmap, imagePath)

        // Then
        assertNotNull(result)
        assertEquals(imagePath, result.imagePath)
        assertTrue(result.detections.isEmpty())
        assertTrue(result.processingTimeMs >= 0)
        verify(tensorFlowHelper).preprocessImage(mockBitmap)
        verify(tensorFlowHelper).runInference(any())
        verify(tensorFlowHelper).postProcessOutput(any(), eq(640), eq(480))
    }

    @Test
    fun `detectDamage should return result with detections when damage found`() = runTest {
        // Given
        val mockBitmap = mock<Bitmap> {
            on { width } doReturn 640
            on { height } doReturn 480
        }
        val imagePath = "/test/path/image.jpg"
        
        val mockDetectionResult = TensorFlowLiteHelper.DetectionResult(
            classIndex = 0, // SCRATCH
            confidence = 0.85f,
            left = 100f,
            top = 100f,
            right = 200f,
            bottom = 200f
        )
        
        whenever(tensorFlowHelper.preprocessImage(any())).thenReturn(mock())
        whenever(tensorFlowHelper.runInference(any())).thenReturn(arrayOf(Array(25200) { FloatArray(9) }))
        whenever(tensorFlowHelper.postProcessOutput(any(), any(), any())).thenReturn(listOf(mockDetectionResult))

        // When
        val result = damageDetectionService.detectDamage(mockBitmap, imagePath)

        // Then
        assertNotNull(result)
        assertEquals(imagePath, result.imagePath)
        assertEquals(1, result.detections.size)
        
        val detection = result.detections.first()
        assertEquals(DamageType.SCRATCH, detection.type)
        assertEquals(0.85f, detection.confidence)
        assertEquals(DamageSeverity.SEVERE, detection.severity)
        assertTrue(result.processingTimeMs >= 0)
    }

    @Test
    fun `validateImage should return true for valid bitmap`() {
        // Given
        val mockBitmap = mock<Bitmap> {
            on { width } doReturn 640
            on { height } doReturn 480
            on { isRecycled } doReturn false
        }

        // When
        val isValid = damageDetectionService.validateImage(mockBitmap)

        // Then
        assertTrue(isValid)
    }

    @Test
    fun `validateImage should return false for small bitmap`() {
        // Given
        val mockBitmap = mock<Bitmap> {
            on { width } doReturn 100
            on { height } doReturn 100
            on { isRecycled } doReturn false
        }

        // When
        val isValid = damageDetectionService.validateImage(mockBitmap)

        // Then
        assertTrue(!isValid)
    }

    @Test
    fun `validateImage should return false for recycled bitmap`() {
        // Given
        val mockBitmap = mock<Bitmap> {
            on { width } doReturn 640
            on { height } doReturn 480
            on { isRecycled } doReturn true
        }

        // When
        val isValid = damageDetectionService.validateImage(mockBitmap)

        // Then
        assertTrue(!isValid)
    }
}