package com.webscare.orangelinetrain.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinetrain.domain.model.Stop

interface StopsRepository {

    fun observeStops(): LiveData<List<Stop>>

    suspend fun syncStops()

}
