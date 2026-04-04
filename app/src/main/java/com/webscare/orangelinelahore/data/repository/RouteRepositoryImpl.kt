package com.webscare.orangelinelahore.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.webscare.orangelinelahore.data.local.RouteDao
import com.webscare.orangelinelahore.data.local.RouteStopDao
import com.webscare.orangelinelahore.data.local.model.RouteEntity
import com.webscare.orangelinelahore.data.local.model.RouteStopEntity
import com.webscare.orangelinelahore.data.local.model.toDomain
import com.webscare.orangelinelahore.data.remote.ApiService
import com.webscare.orangelinelahore.domain.model.Route
import com.webscare.orangelinelahore.domain.repository.RouteRepository
import javax.inject.Inject

class RouteRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val routeDao: RouteDao,
    private val routeStopDao: RouteStopDao
) : RouteRepository {

    override fun observeRoutes(): LiveData<List<Route>> =
        routeDao.observeRoutesWithStops().map { list ->
            list.map { item ->
                item.route.toDomain(
                    stops = item.stops.map { it.toDomain() })
            }
        }

    override suspend fun syncRoutes() {
        try {
            val remote = api.getRoutes().data

            // 1️⃣ SAVE ROUTES
            routeDao.insertRoutes(
                remote.map {
                    RouteEntity(
                        id = it.id,
                        cityId = it.city_id,
                        name = it.name,
                        start = it.start,
                        end = it.end,
                        jsonFile = it.json_file,
                        numberOfBuses = it.number_of_buses,
                        ticketPrice = it.route_ticket_price,
                        rideDistance = it.ride_distance,
                        totalRideTime = it.total_ride_time,
                        firstRideTime = it.first_ride_time,
                        lastRideTime = it.last_ride_time,
                        leaveBus = it.leave_bus
                    )
                })

            // 2️⃣ SAVE ROUTE–STOP ORDER
            routeStopDao.insertAll(
                remote.flatMap { route ->
                    route.stops.mapIndexed { index, stop ->
                        RouteStopEntity(
                            routeId = route.id, stopId = stop.id, orderIndex = index
                        )
                    }
                })
        } catch (e: Exception) {
            Log.e("SYNC_ERROR", "Routes sync failed: ${e.message}")
        }
    }

}