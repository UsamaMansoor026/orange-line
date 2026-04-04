package com.webscare.orangelinelahore.domain.model

data class DirectionsRequest(
    val origin: String,
    val destination: String,
    val waypoints: String?
)