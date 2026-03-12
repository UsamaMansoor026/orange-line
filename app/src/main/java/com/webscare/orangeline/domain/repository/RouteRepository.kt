package com.webscare.orangeline.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangeline.domain.model.Route

interface RouteRepository {
    fun observeRoutes(): LiveData<List<Route>>
    suspend fun syncRoutes()
}
