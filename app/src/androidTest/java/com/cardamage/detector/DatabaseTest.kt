package com.cardamage.detector

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardamage.detector.data.dao.DamageResultDao
import com.cardamage.detector.data.database.DamageDetectorDatabase
import com.cardamage.detector.data.database.DamageResultEntity
import com.cardamage.detector.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: DamageDetectorDatabase
    private lateinit var dao: DamageResultDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DamageDetectorDatabase::class.java
        ).build()
        dao = database.damageResultDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveDamageResult() = runTest {
        // Given
        val damageDetection = DamageDetection(
            type = DamageType.SCRATCH,
            severity = DamageSeverity.MINOR,
            confidence = 0.85f,
            boundingBox = BoundingBox(100f, 100f, 200f, 200f),
            location = "Front Left"
        )
        
        val entity = DamageResultEntity(
            imagePath = "/test/path/image.jpg",
            detections = listOf(damageDetection),
            timestamp = System.currentTimeMillis(),
            processingTimeMs = 1500L
        )

        // When
        val id = dao.insertResult(entity)
        val retrieved = dao.getResultById(id)

        // Then
        assertNotNull(retrieved)
        assertEquals(entity.imagePath, retrieved.imagePath)
        assertEquals(entity.detections.size, retrieved.detections.size)
        assertEquals(entity.detections.first().type, retrieved.detections.first().type)
        assertEquals(entity.processingTimeMs, retrieved.processingTimeMs)
    }

    @Test
    fun getAllResultsOrderedByTimestamp() = runTest {
        // Given
        val entity1 = DamageResultEntity(
            imagePath = "/test/path/image1.jpg",
            detections = emptyList(),
            timestamp = 1000L,
            processingTimeMs = 1000L
        )
        
        val entity2 = DamageResultEntity(
            imagePath = "/test/path/image2.jpg",
            detections = emptyList(),
            timestamp = 2000L,
            processingTimeMs = 1200L
        )

        // When
        dao.insertResult(entity1)
        dao.insertResult(entity2)
        
        val results = dao.getAllResults().first()

        // Then
        assertEquals(2, results.size)
        // Should be ordered by timestamp DESC (newest first)
        assertEquals(2000L, results[0].timestamp)
        assertEquals(1000L, results[1].timestamp)
    }

    @Test
    fun deleteResult() = runTest {
        // Given
        val entity = DamageResultEntity(
            imagePath = "/test/path/image.jpg",
            detections = emptyList(),
            timestamp = System.currentTimeMillis(),
            processingTimeMs = 1000L
        )

        // When
        val id = dao.insertResult(entity)
        dao.deleteResultById(id)
        
        val retrieved = dao.getResultById(id)

        // Then
        assertEquals(null, retrieved)
    }

    @Test
    fun getResultCount() = runTest {
        // Given
        val entity1 = DamageResultEntity(
            imagePath = "/test/path/image1.jpg",
            detections = emptyList(),
            timestamp = System.currentTimeMillis(),
            processingTimeMs = 1000L
        )
        
        val entity2 = DamageResultEntity(
            imagePath = "/test/path/image2.jpg",
            detections = listOf(
                DamageDetection(
                    type = DamageType.DENT,
                    severity = DamageSeverity.MODERATE,
                    confidence = 0.75f,
                    boundingBox = BoundingBox(50f, 50f, 150f, 150f)
                )
            ),
            timestamp = System.currentTimeMillis(),
            processingTimeMs = 1200L
        )

        // When
        dao.insertResult(entity1)
        dao.insertResult(entity2)
        
        val totalCount = dao.getResultCount()
        val damageCount = dao.getResultsWithDamageCount()

        // Then
        assertEquals(2, totalCount)
        assertEquals(1, damageCount)
    }
}