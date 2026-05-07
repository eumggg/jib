package com.jib.app.data.repository

import com.jib.app.data.model.Station
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    fun getStations(swLat: Double, swLng: Double, neLat: Double, neLng: Double): Flow<List<Station>>
}
