package com.webscare.orangeline.domain.model

data class RouteMeta(
        val start: String,
        val end: String,
        val stopCount: Int,
        val distance: Double,
        val duration: Int
    )