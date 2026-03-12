package com.webscare.orangeline.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.orangeline.domain.model.City
import com.webscare.orangeline.domain.model.Route
import com.webscare.orangeline.domain.model.Stop

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: Int,

    val cityId: Int,
    val name: String,
    val start: String,
    val end: String,
    val jsonFile: String?,
    val numberOfBuses: Int,
    val ticketPrice: Int,
    val rideDistance: String,
    val totalRideTime: Int,
    val firstRideTime: String,
    val lastRideTime: String,
    val leaveBus: Int
)

fun RouteEntity.toDomain(stops: List<Stop>): Route =
    Route(
        id = id,
        city_id = cityId,
        stop_ids = "",
        name = name,
        start = start,
        end = end,
        json_file = jsonFile,
        number_of_buses = numberOfBuses,
        route_ticket_price = ticketPrice,
        ride_distance = rideDistance,
        total_ride_time = totalRideTime,
        first_ride_time = firstRideTime,
        last_ride_time = lastRideTime,
        leave_bus = leaveBus,
        stops = stops,
        city = City(cityId, "")
    )
