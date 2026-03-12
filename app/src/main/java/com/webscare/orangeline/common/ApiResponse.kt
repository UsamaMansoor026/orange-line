package com.webscare.orangeline.common

data class ApiResponse<T>(
    val message: String,
    val data: T
)