package com.webscare.orangelinelahore.data.local.model

import androidx.room.Entity

@Entity(
    tableName = "route_stops",
    primaryKeys = ["routeId", "stopId"]
)
data class RouteStopEntity(
    val routeId: Int,
    val stopId: Int,
    val orderIndex: Int
)

