package com.cardamage.detector.di

import android.content.Context
import com.cardamage.detector.api.RoboflowClient
import com.cardamage.detector.gallery.ImagePicker
import com.cardamage.detector.ml.DamageDetectionService
import com.cardamage.detector.ml.TensorFlowLiteHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTensorFlowLiteHelper(@ApplicationContext context: Context): TensorFlowLiteHelper {
        return TensorFlowLiteHelper(context)
    }

    @Provides
    @Singleton
    fun provideDamageDetectionService(
        tensorFlowHelper: TensorFlowLiteHelper,
        roboflowClient: RoboflowClient
    ): DamageDetectionService {
        return DamageDetectionService(tensorFlowHelper, roboflowClient)
    }

    @Provides
    @Singleton
    fun provideImagePicker(@ApplicationContext context: Context): ImagePicker {
        return ImagePicker(context)
    }

    @Provides
    @Singleton
    fun provideRoboflowClient(@ApplicationContext context: Context): RoboflowClient {
        return RoboflowClient(context)
    }
}