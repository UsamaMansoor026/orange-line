package com.webscare.orangelinetrain.domain.model

import java.time.LocalTime

data class Departure(
    val routeName: String,
    val destination: String,
    val scheduledTime: LocalTime,
    val minutesDiff: Long
)