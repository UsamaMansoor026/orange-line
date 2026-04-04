package com.webscare.orangelinetrain.data.remote

import com.webscare.orangelinetrain.common.ApiResponse
import com.webscare.orangelinetrain.domain.model.City
import com.webscare.orangelinetrain.domain.model.Route
import com.webscare.orangelinetrain.domain.model.Stop
import retrofit2.http.GET

interface ApiService {

    @GET("api/allStopList")
    suspend fun getStops(): ApiResponse<List<Stop>>

    @GET("api/cities")
    suspend fun getCities(): ApiResponse<List<City>>

    @GET("api/routes")
    suspend fun getRoutes(): ApiResponse<List<Route>>
}
