package com.webscare.orangelinetrain.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinetrain.domain.model.Route

interface RouteRepository {
    fun observeRoutes(): LiveData<List<Route>>
    suspend fun syncRoutes()
}
