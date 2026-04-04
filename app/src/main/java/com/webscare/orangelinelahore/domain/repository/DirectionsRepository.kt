package com.webscare.orangelinelahore.domain.repository

import com.google.android.gms.maps.model.LatLng

interface DirectionsRepository {

    suspend fun loadRoute(): List<LatLng>
}
