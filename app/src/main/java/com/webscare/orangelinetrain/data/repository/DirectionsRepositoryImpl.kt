package com.webscare.orangelinetrain.data.repository

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.webscare.orangelinetrain.data.local.model.GeoJsonRoute
import com.webscare.orangelinetrain.domain.repository.DirectionsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DirectionsRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : DirectionsRepository {

    // Cache so we only parse the JSON once
    private var cachedTrack: List<LatLng>? = null

    override suspend fun loadRoute(): List<LatLng> {
        cachedTrack?.let { return it }  // return cached if available

        return try {
            val json = appContext.assets
                .open("route_track.json")
                .bufferedReader()
                .use { it.readText() }

            val geoRoute = Gson().fromJson(json, GeoJsonRoute::class.java)

            val coordinates = geoRoute
                .features
                .first()
                .geometry
                .coordinates

            val track = coordinates.map { coord -> LatLng(coord[1], coord[0]) }
            cachedTrack = track  // save it
            track

        } catch (e: Exception) {
            android.util.Log.e("DirectionsRepo", "Failed to load route: ${e.message}", e)
            emptyList()  // never crash, always return safely
        }
    }
}