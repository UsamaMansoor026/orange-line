package com.webscare.orangeline.data.remote

import com.webscare.orangeline.common.ApiResponse
import com.webscare.orangeline.domain.model.City
import com.webscare.orangeline.domain.model.Route
import com.webscare.orangeline.domain.model.Stop
import retrofit2.http.GET

interface ApiService {

    @GET("api/allStopList")
    suspend fun getStops(): ApiResponse<List<Stop>>

    @GET("api/cities")
    suspend fun getCities(): ApiResponse<List<City>>

    @GET("api/routes")
    suspend fun getRoutes(): ApiResponse<List<Route>>
}
