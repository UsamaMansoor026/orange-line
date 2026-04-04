package com.webscare.orangelinetrain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.webscare.orangelinetrain.data.local.model.CityEntity
import com.webscare.orangelinetrain.data.local.model.RouteEntity
import com.webscare.orangelinetrain.data.local.model.RouteStopEntity
import com.webscare.orangelinetrain.data.local.model.StopEntity

@Database(
    entities = [
        CityEntity::class,
        StopEntity::class,
        RouteEntity::class,
        RouteStopEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun routeStopDao(): RouteStopDao
}
