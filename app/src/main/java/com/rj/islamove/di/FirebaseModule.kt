package com.rj.islamove.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.rj.islamove.data.repository.LandmarkRepository
import com.rj.islamove.data.repository.ServiceAreaManagementRepository
import com.rj.islamove.data.repository.BoundaryFareManagementRepository
import com.rj.islamove.data.repository.ZoneBoundaryRepository
import com.rj.islamove.data.repository.MapboxPlacesRepository
import com.rj.islamove.data.repository.MapboxGeocodingRepository
import com.rj.islamove.data.repository.SupportCommentRepository
import com.rj.islamove.data.api.MapboxBoundariesService
import com.rj.islamove.data.api.RenderApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton
import com.rj.islamove.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

//    @Provides
//    @Singleton
//    fun provideFirebaseFunctions(): FirebaseFunctions {
//        return FirebaseFunctions.getInstance()
//    }

    @Provides
    @Singleton
    fun provideRenderApiService(client: OkHttpClient): RenderApiService {
        return RenderApiService(BuildConfig.RENDER_BASE_URL, client)
    }
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideLandmarkRepository(firestore: FirebaseFirestore): LandmarkRepository {
        return LandmarkRepository(firestore)
    }

    @Provides
    @Singleton
    fun provideServiceAreaManagementRepository(firestore: FirebaseFirestore): ServiceAreaManagementRepository {
        return ServiceAreaManagementRepository(firestore)
    }

    @Provides
    @Singleton
    fun provideBoundaryFareManagementRepository(firestore: FirebaseFirestore): BoundaryFareManagementRepository {
        return BoundaryFareManagementRepository(firestore)
    }

    @Provides
    @Singleton
    fun provideZoneBoundaryRepository(firestore: FirebaseFirestore): ZoneBoundaryRepository {
        return ZoneBoundaryRepository(firestore)
    }

    @Provides
    @Singleton
    fun provideMapboxPlacesRepository(@ApplicationContext context: Context): MapboxPlacesRepository {
        return MapboxPlacesRepository(context)
    }

    @Provides
    @Singleton
    fun provideMapboxGeocodingRepository(
        @ApplicationContext context: Context,
        mapboxPlacesRepository: MapboxPlacesRepository
    ): MapboxGeocodingRepository {
        return MapboxGeocodingRepository(context, mapboxPlacesRepository)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideMapboxBoundariesService(httpClient: OkHttpClient): MapboxBoundariesService {
        return MapboxBoundariesService(httpClient)
    }

    @Provides
    @Singleton
    fun provideSupportCommentRepository(firestore: FirebaseFirestore): SupportCommentRepository {
        return SupportCommentRepository(firestore)
    }
}