package com.webscare.orangelinetrain.data.local.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RouteWithStops(
    @Embedded val route: RouteEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RouteStopEntity::class,
            parentColumn = "routeId",
            entityColumn = "stopId"
        )
    )
    val stops: List<StopEntity>
)
