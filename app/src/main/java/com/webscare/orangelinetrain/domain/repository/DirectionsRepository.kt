package com.webscare.orangelinetrain.domain.repository

import com.google.android.gms.maps.model.LatLng

interface DirectionsRepository {

    suspend fun loadRoute(): List<LatLng>
}
