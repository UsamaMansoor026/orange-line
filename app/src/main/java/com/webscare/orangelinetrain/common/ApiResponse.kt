package com.webscare.orangelinetrain.common

data class ApiResponse<T>(
    val message: String,
    val data: T
)