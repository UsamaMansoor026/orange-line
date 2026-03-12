package com.webscare.orangeline.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webscare.orangeline.domain.model.City

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val id: Int,
    val name: String
)

fun CityEntity.toDomain() = City(id, name)
