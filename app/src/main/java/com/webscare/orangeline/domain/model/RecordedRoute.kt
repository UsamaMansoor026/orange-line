package com.webscare.orangeline.domain.model

data class RecordedRoute(
    val route_name: String,
    val city: String,
    val direction: String,
    val polyline_points: List<RoutePoint>
)
