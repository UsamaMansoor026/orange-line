package com.webscare.orangeline.domain.model

data class DirectionsRequest(
    val origin: String,
    val destination: String,
    val waypoints: String?
)