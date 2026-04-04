package com.webscare.orangelinelahore.domain.repository

import androidx.lifecycle.LiveData
import com.webscare.orangelinelahore.domain.model.Stop

interface StopsRepository {

    fun observeStops(): LiveData<List<Stop>>

    suspend fun syncStops()

}
