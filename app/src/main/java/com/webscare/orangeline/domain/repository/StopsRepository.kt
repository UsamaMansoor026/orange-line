package com.webscare.orangeline.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangeline.domain.model.Stop

interface StopsRepository {

    fun observeStops(): LiveData<List<Stop>>

    suspend fun syncStops()

}
