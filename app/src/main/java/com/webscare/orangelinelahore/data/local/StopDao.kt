package com.webscare.orangelinelahore.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.webscare.orangelinelahore.data.local.model.StopEntity

@Dao
interface StopDao {

    @Query("SELECT * FROM stops ORDER BY id ASC")
    fun observeStops(): LiveData<List<StopEntity>>

    @Query("""
        SELECT s.*
        FROM stops s
        INNER JOIN route_stops rs ON rs.stopId = s.id
        WHERE rs.routeId = :routeId
        ORDER BY rs.orderIndex ASC
    """)
    suspend fun getStopsForRoute(routeId: Int): List<StopEntity>

    @Query("DELETE FROM stops")
    suspend fun clearStops()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)
}
