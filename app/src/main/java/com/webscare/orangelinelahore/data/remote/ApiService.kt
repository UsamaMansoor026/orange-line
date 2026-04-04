package com.webscare.orangelinelahore.data.remote

import com.webscare.orangelinelahore.common.ApiResponse
import com.webscare.orangelinelahore.domain.model.City
import com.webscare.orangelinelahore.domain.model.Route
import com.webscare.orangelinelahore.domain.model.Stop
import retrofit2.http.GET

interface ApiService {

    @GET("api/allStopList")
    suspend fun getStops(): ApiResponse<List<Stop>>

    @GET("api/cities")
    suspend fun getCities(): ApiResponse<List<City>>

    @GET("api/routes")
    suspend fun getRoutes(): ApiResponse<List<Route>>
}
