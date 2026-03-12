package com.webscare.orangeline.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.webscare.orangeline.data.local.model.CityEntity

@Dao
interface CityDao {

    @Query("SELECT * FROM cities")
    fun observeCities(): LiveData<List<CityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cities: List<CityEntity>)
}

