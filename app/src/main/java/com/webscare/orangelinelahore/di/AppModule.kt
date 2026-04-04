package com.webscare.orangelinelahore.di

import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.webscare.orangelinelahore.data.local.AppDatabase
import com.webscare.orangelinelahore.data.local.CityDao
import com.webscare.orangelinelahore.data.local.RouteDao
import com.webscare.orangelinelahore.data.local.RouteStopDao
import com.webscare.orangelinelahore.data.local.StopDao
import com.webscare.orangelinelahore.data.remote.ApiService
import com.webscare.orangelinelahore.data.remote.DirectionsApiService
import com.webscare.orangelinelahore.data.repository.CityRepositoryImpl
import com.webscare.orangelinelahore.data.repository.DirectionsRepositoryImpl
import com.webscare.orangelinelahore.data.repository.RouteRepositoryImpl
import com.webscare.orangelinelahore.data.repository.StopsRepositoryImpl
import com.webscare.orangelinelahore.domain.repository.CityRepository
import com.webscare.orangelinelahore.domain.repository.DirectionsRepository
import com.webscare.orangelinelahore.domain.repository.RouteRepository
import com.webscare.orangelinelahore.domain.repository.StopsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit =
        Retrofit.Builder().baseUrl("https://metro.shabbirhussain.com/")
            .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides
    @Singleton
    @Named("google")
    fun provideGoogleRetrofit(): Retrofit =
        Retrofit.Builder().baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides
    @Singleton
    fun provideDirectionsApi(
        @Named("google") retrofit: Retrofit
    ): DirectionsApiService = retrofit.create(DirectionsApiService::class.java)

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, "train_map_db"
    ).build()

    @Provides
    fun provideStopDao(db: AppDatabase): StopDao = db.stopDao()

    @Provides
    fun provideCityDao(db: AppDatabase): CityDao = db.cityDao()

    @Provides
    fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideRouteStopsDao(db: AppDatabase): RouteStopDao = db.routeStopDao()

    @Provides
    @Singleton
    fun provideStopsRepository(
        impl: StopsRepositoryImpl
    ): StopsRepository = impl

    @Provides
    @Singleton
    fun provideLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Named("maps_api_key")
    fun provideMapsApiKey(): String = "AIzaSyD4ZWQxs6BrtKjeD0YUMxKQxwZ9h4GGKGk"

    @Provides
    fun provideCityRepository(
        api: ApiService, dao: CityDao
    ): CityRepository = CityRepositoryImpl(api, dao)

    @Provides
    fun provideRouteRepository(
        api: ApiService, routeDao: RouteDao, routeStopDao: RouteStopDao
    ): RouteRepository = RouteRepositoryImpl(api, routeDao, routeStopDao)

    @Provides
    @Singleton
    fun provideDirectionsRepository(
        impl: DirectionsRepositoryImpl
    ): DirectionsRepository = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    }

}