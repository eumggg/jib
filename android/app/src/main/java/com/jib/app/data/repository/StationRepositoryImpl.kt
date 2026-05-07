package com.jib.app.data.repository

import com.google.gson.Gson
import com.jib.app.data.local.StationDao
import com.jib.app.data.model.Station
import com.jib.app.data.remote.StationApiService
import com.jib.app.data.remote.StationDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val api: StationApiService,
    private val dao: StationDao,
) : StationRepository {

    // Offline-first lens: Room emits cached rows immediately; network refresh upserts on top.
    override fun getStations(
        swLat: Double, swLng: Double, neLat: Double, neLng: Double,
    ): Flow<List<Station>> =
        dao.getStationsInBounds(swLat, swLng, neLat, neLng)
            .onStart {
                try {
                    val remote = api.getStations(swLat, swLng, neLat, neLng)
                    dao.upsertAll(remote.stations.map { it.toEntity() })
                } catch (_: Exception) {
                    // Network unavailable — Room cache serves below.
                }
            }

    private fun StationDto.toEntity() = Station(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        connectorTypes = Gson().toJson(connectorTypes),
        powerKw = powerKw,
        networkOperator = networkOperator,
        isAvailable = isAvailable,
    )
}
