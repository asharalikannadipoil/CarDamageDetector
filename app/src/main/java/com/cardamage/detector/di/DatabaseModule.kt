package com.cardamage.detector.di

import android.content.Context
import androidx.room.Room
import com.cardamage.detector.data.dao.DamageResultDao
import com.cardamage.detector.data.database.DamageDetectorDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DamageDetectorDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            DamageDetectorDatabase::class.java,
            "damage_detector_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDamageResultDao(database: DamageDetectorDatabase): DamageResultDao {
        return database.damageResultDao()
    }
}