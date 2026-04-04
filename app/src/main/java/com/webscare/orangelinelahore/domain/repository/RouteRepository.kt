package com.webscare.orangelinelahore.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinelahore.domain.model.Route

interface RouteRepository {
    fun observeRoutes(): LiveData<List<Route>>
    suspend fun syncRoutes()
}
