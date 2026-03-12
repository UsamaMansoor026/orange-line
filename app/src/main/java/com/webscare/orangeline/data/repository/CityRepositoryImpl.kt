package com.webscare.orangeline.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.webscare.orangeline.data.local.CityDao
import com.webscare.orangeline.data.local.model.CityEntity
import com.webscare.orangeline.data.local.model.toDomain
import com.webscare.orangeline.data.remote.ApiService
import com.webscare.orangeline.domain.model.City
import com.webscare.orangeline.domain.repository.CityRepository
import javax.inject.Inject

class CityRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: CityDao
) : CityRepository {

    override fun observeCities(): LiveData<List<City>> =
        dao.observeCities().map { it.map { c -> c.toDomain() } }

    override suspend fun syncCities() {

        try {
            val remote = api.getCities().data

            dao.insertAll(
                remote.map {
                    CityEntity(
                        id = it.id,
                        name = it.name
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("DIR_API", "Failed to fetch directions: ${e.message}")
        }
    }

}
