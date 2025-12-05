package com.cardamage.detector.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.cardamage.detector.data.dao.DamageResultDao

@Database(
    entities = [DamageResultEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DamageDetectorDatabase : RoomDatabase() {
    
    abstract fun damageResultDao(): DamageResultDao
    
    companion object {
        @Volatile
        private var INSTANCE: DamageDetectorDatabase? = null
        
        fun getDatabase(context: Context): DamageDetectorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DamageDetectorDatabase::class.java,
                    "damage_detector_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}