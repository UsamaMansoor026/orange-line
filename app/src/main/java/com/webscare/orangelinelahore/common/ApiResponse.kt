package com.webscare.orangelinelahore.common

data class ApiResponse<T>(
    val message: String,
    val data: T
)