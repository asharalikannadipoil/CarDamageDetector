package com.cardamage.detector.data.repository

import com.cardamage.detector.data.dao.DamageResultDao
import com.cardamage.detector.data.database.DamageResultEntity
import com.cardamage.detector.data.database.toAnalysisResult
import com.cardamage.detector.data.database.toEntity
import com.cardamage.detector.data.model.DamageAnalysisResult
import com.cardamage.detector.data.model.DamageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DamageResultRepository @Inject constructor(
    private val damageResultDao: DamageResultDao
) {
    
    fun getAllResults(): Flow<List<DamageAnalysisResult>> {
        return damageResultDao.getAllResults().map { entities ->
            entities.map { it.toAnalysisResult() }
        }
    }
    
    suspend fun getResultById(id: Long): DamageAnalysisResult? {
        return damageResultDao.getResultById(id)?.toAnalysisResult()
    }
    
    suspend fun getRecentResults(limit: Int = 10): List<DamageAnalysisResult> {
        return damageResultDao.getRecentResults(limit).map { it.toAnalysisResult() }
    }
    
    suspend fun getResultsFromDate(fromTimestamp: Long): List<DamageAnalysisResult> {
        return damageResultDao.getResultsFromDate(fromTimestamp).map { it.toAnalysisResult() }
    }
    
    suspend fun getResultCount(): Int {
        return damageResultDao.getResultCount()
    }
    
    suspend fun getResultsWithDamageCount(): Int {
        return damageResultDao.getResultsWithDamageCount()
    }
    
    suspend fun saveResult(result: DamageAnalysisResult, notes: String? = null): Long {
        return damageResultDao.insertResult(result.toEntity(notes))
    }
    
    suspend fun updateResult(result: DamageAnalysisResult, notes: String? = null) {
        val entity = result.toEntity(notes)
        damageResultDao.updateResult(entity)
    }
    
    suspend fun deleteResult(resultId: Long) {
        damageResultDao.deleteResultById(resultId)
    }
    
    suspend fun deleteAllResults() {
        damageResultDao.deleteAllResults()
    }
    
    suspend fun deleteOldResults(beforeTimestamp: Long) {
        damageResultDao.deleteResultsOlderThan(beforeTimestamp)
    }
    
    suspend fun searchResults(searchTerm: String): List<DamageAnalysisResult> {
        return damageResultDao.searchResults(searchTerm).map { it.toAnalysisResult() }
    }
    
    suspend fun getResultsByDamageType(damageType: DamageType): List<DamageAnalysisResult> {
        return damageResultDao.getResultsByDamageType(damageType.name).map { it.toAnalysisResult() }
    }
    
    suspend fun getStatistics(): AnalysisStatistics {
        val totalResults = getResultCount()
        val resultsWithDamage = getResultsWithDamageCount()
        val recentResults = getRecentResults(100)
        
        val damageTypeCounts = recentResults
            .flatMap { it.detections }
            .groupingBy { it.type }
            .eachCount()
        
        val averageProcessingTime = recentResults
            .map { it.processingTimeMs }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return AnalysisStatistics(
            totalAnalyses = totalResults,
            analysesWithDamage = resultsWithDamage,
            analysesWithoutDamage = totalResults - resultsWithDamage,
            damageTypeCounts = damageTypeCounts,
            averageProcessingTimeMs = averageProcessingTime.toLong()
        )
    }
}

data class AnalysisStatistics(
    val totalAnalyses: Int,
    val analysesWithDamage: Int,
    val analysesWithoutDamage: Int,
    val damageTypeCounts: Map<DamageType, Int>,
    val averageProcessingTimeMs: Long
)