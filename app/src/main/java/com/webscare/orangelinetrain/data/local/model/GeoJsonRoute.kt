package com.webscare.orangelinetrain.data.local.model

data class GeoJsonRoute(
    val type: String,
    val features: List<Feature>
)