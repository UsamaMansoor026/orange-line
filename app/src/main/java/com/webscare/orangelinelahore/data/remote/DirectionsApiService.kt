package com.webscare.orangelinelahore.data.remote

import com.webscare.orangelinelahore.domain.model.DirectionsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {

    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("mode") mode: String = "driving",
        @Query("units") units: String = "metric",
        @Query("key") apiKey: String
    ): DirectionsResponse
}
