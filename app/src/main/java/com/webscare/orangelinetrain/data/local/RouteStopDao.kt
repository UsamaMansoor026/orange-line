package com.webscare.orangelinetrain.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.webscare.orangelinetrain.data.local.model.RouteStopEntity

@Dao
interface RouteStopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RouteStopEntity>)
}
