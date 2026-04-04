package com.webscare.orangelinelahore.domain.model

data class Stop(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val city_id: Int,
    val city: City,
    val is_underground: Boolean
)
