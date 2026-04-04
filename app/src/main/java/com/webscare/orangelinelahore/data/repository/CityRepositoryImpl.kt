package com.webscare.orangelinelahore.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.webscare.orangelinelahore.data.local.CityDao
import com.webscare.orangelinelahore.data.local.model.CityEntity
import com.webscare.orangelinelahore.data.local.model.toDomain
import com.webscare.orangelinelahore.data.remote.ApiService
import com.webscare.orangelinelahore.domain.model.City
import com.webscare.orangelinelahore.domain.repository.CityRepository
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
