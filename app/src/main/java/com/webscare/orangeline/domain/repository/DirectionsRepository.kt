package com.webscare.orangeline.domain.repository

import com.google.android.gms.maps.model.LatLng

interface DirectionsRepository {

    suspend fun loadRoute(): List<LatLng>
}
