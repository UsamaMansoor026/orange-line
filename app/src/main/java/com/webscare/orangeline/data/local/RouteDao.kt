package com.webscare.orangeline.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.webscare.orangeline.data.local.model.RouteEntity
import com.webscare.orangeline.data.local.model.RouteWithStops

@Dao
interface RouteDao {

    @Transaction
    @Query("SELECT * FROM routes")
    fun observeRoutesWithStops(): LiveData<List<RouteWithStops>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)
}

