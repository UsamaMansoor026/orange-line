package com.webscare.orangelinetrain.data.local.model

data class Geometry(
    val type: String,
    val coordinates: List<List<Double>> // [lng, lat]
)