package com.webscare.orangelinelahore.data.repository

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.webscare.orangelinelahore.data.local.model.GeoJsonRoute
import com.webscare.orangelinelahore.domain.repository.DirectionsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DirectionsRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : DirectionsRepository {

    override suspend fun loadRoute(): List<LatLng> {

        val json = appContext.assets
            .open("route_track.json")   // your new file name
            .bufferedReader()
            .use { it.readText() }

        val geoRoute = Gson().fromJson(json, GeoJsonRoute::class.java)

        val coordinates = geoRoute
            .features
            .first()
            .geometry
            .coordinates

        return coordinates.map { coord ->
            LatLng(coord[1], coord[0])
        }
    }
}

