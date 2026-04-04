package com.webscare.orangelinelahore.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinelahore.domain.model.City

interface CityRepository {
    fun observeCities(): LiveData<List<City>>
    suspend fun syncCities()
}
