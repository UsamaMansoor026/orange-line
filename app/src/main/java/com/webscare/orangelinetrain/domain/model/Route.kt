package com.webscare.orangelinetrain.domain.model

data class Route(
    val id: Int,
    val city_id: Int,
    val stop_ids: String,

    val name: String,
    val start: String,
    val end: String,
    val json_file: String?,
    val number_of_buses: Int,
    val route_ticket_price: Int,
    val ride_distance: String,
    val total_ride_time: Int,
    val first_ride_time: String,
    val last_ride_time: String,
    val leave_bus: Int,

    val stops: List<Stop>,
    val city: City
)

