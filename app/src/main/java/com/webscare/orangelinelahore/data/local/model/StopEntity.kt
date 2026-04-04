package com.webscare.orangelinelahore.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.orangelinelahore.domain.model.City
import com.webscare.orangelinelahore.domain.model.Stop

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val id: Int,

    val name: String,
    val latitude: Double,
    val longitude: Double,

    val cityId: Int,
    val cityName: String
)

fun StopEntity.toDomain(): Stop =
    Stop(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        city_id = cityId,
        city = City(cityId, cityName),
        false
    )

