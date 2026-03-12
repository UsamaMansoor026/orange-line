package com.webscare.orangeline.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangeline.domain.model.City

interface CityRepository {
    fun observeCities(): LiveData<List<City>>
    suspend fun syncCities()
}
