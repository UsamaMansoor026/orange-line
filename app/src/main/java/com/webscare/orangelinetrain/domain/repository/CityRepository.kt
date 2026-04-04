package com.webscare.orangelinetrain.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinetrain.domain.model.City

interface CityRepository {
    fun observeCities(): LiveData<List<City>>
    suspend fun syncCities()
}
