package com.cardamage.detector.data.dao

import androidx.room.*
import com.cardamage.detector.data.database.DamageResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DamageResultDao {
    
    @Query("SELECT * FROM damage_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<DamageResultEntity>>
    
    @Query("SELECT * FROM damage_results WHERE id = :id")
    suspend fun getResultById(id: Long): DamageResultEntity?
    
    @Query("SELECT * FROM damage_results ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentResults(limit: Int = 10): List<DamageResultEntity>
    
    @Query("SELECT * FROM damage_results WHERE timestamp >= :fromTimestamp ORDER BY timestamp DESC")
    suspend fun getResultsFromDate(fromTimestamp: Long): List<DamageResultEntity>
    
    @Query("SELECT COUNT(*) FROM damage_results")
    suspend fun getResultCount(): Int
    
    @Query("SELECT COUNT(*) FROM damage_results WHERE JSON_ARRAY_LENGTH(detections) > 0")
    suspend fun getResultsWithDamageCount(): Int
    
    @Insert
    suspend fun insertResult(result: DamageResultEntity): Long
    
    @Update
    suspend fun updateResult(result: DamageResultEntity)
    
    @Delete
    suspend fun deleteResult(result: DamageResultEntity)
    
    @Query("DELETE FROM damage_results WHERE id = :id")
    suspend fun deleteResultById(id: Long)
    
    @Query("DELETE FROM damage_results")
    suspend fun deleteAllResults()
    
    @Query("DELETE FROM damage_results WHERE timestamp < :beforeTimestamp")
    suspend fun deleteResultsOlderThan(beforeTimestamp: Long)
    
    @Query("SELECT * FROM damage_results WHERE imagePath LIKE '%' || :searchTerm || '%' ORDER BY timestamp DESC")
    suspend fun searchResults(searchTerm: String): List<DamageResultEntity>
    
    @Query("""
        SELECT * FROM damage_results 
        WHERE JSON_EXTRACT(detections, '$[*].type') LIKE '%' || :damageType || '%' 
        ORDER BY timestamp DESC
    """)
    suspend fun getResultsByDamageType(damageType: String): List<DamageResultEntity>
}