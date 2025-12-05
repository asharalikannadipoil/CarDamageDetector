package com.cardamage.detector.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cardamage.detector.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "damage_results")
@TypeConverters(Converters::class)
data class DamageResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val detections: List<DamageDetection>,
    val timestamp: Long,
    val processingTimeMs: Long,
    val notes: String? = null
)

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDetectionList(value: List<DamageDetection>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toDetectionList(value: String): List<DamageDetection> {
        val listType = object : TypeToken<List<DamageDetection>>() {}.type
        return gson.fromJson(value, listType)
    }
    
    @TypeConverter
    fun fromDamageType(value: DamageType): String {
        return value.name
    }
    
    @TypeConverter
    fun toDamageType(value: String): DamageType {
        return DamageType.valueOf(value)
    }
    
    @TypeConverter
    fun fromDamageSeverity(value: DamageSeverity): String {
        return value.name
    }
    
    @TypeConverter
    fun toDamageSeverity(value: String): DamageSeverity {
        return DamageSeverity.valueOf(value)
    }
    
    @TypeConverter
    fun fromBoundingBox(value: BoundingBox): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toBoundingBox(value: String): BoundingBox {
        return gson.fromJson(value, BoundingBox::class.java)
    }
}

fun DamageAnalysisResult.toEntity(notes: String? = null): DamageResultEntity {
    return DamageResultEntity(
        imagePath = imagePath,
        detections = detections,
        timestamp = timestamp,
        processingTimeMs = processingTimeMs,
        notes = notes
    )
}

fun DamageResultEntity.toAnalysisResult(): DamageAnalysisResult {
    return DamageAnalysisResult(
        imagePath = imagePath,
        detections = detections,
        timestamp = timestamp,
        processingTimeMs = processingTimeMs
    )
}