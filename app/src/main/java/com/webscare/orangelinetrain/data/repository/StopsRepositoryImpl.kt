package com.webscare.orangelinetrain.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.webscare.orangelinetrain.data.local.StopDao
import com.webscare.orangelinetrain.data.local.model.StopEntity
import com.webscare.orangelinetrain.data.local.model.toDomain
import com.webscare.orangelinetrain.data.remote.ApiService
import com.webscare.orangelinetrain.domain.model.Stop
import com.webscare.orangelinetrain.domain.repository.StopsRepository
import javax.inject.Inject

class StopsRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: StopDao
) : StopsRepository {

    override fun observeStops(): LiveData<List<Stop>> =
        dao.observeStops().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun syncStops() {
        try {
            val response = api.getStops()
            val remote = response.data
            dao.clearStops()
            dao.insertAll(
                remote.map {
                    StopEntity(
                        id = it.id,
                        name = it.name,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        cityId = it.city_id,
                        cityName = it.city.name
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Stops sync failed: ${e.message}")
        }
    }
}
