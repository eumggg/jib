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
    // The connector filter is applied at both layers so both the cached and refreshed sets
    // honor the active filter.
    override fun getStations(
        swLat: Double, swLng: Double, neLat: Double, neLng: Double,
        connectorType: String?,
    ): Flow<List<Station>> {
        val cached = if (connectorType == null) {
            dao.getStationsInBounds(swLat, swLng, neLat, neLng)
        } else {
            dao.getStationsInBoundsByConnector(swLat, swLng, neLat, neLng, connectorType)
        }
        return cached.onStart {
            try {
                val remote = api.getStations(swLat, swLng, neLat, neLng, connectorType)
                dao.upsertAll(remote.stations.map { it.toEntity() })
            } catch (_: Exception) {
                // Network unavailable — Room cache serves below.
            }
        }
    }

    override fun getStation(id: String): Flow<Station?> =
        dao.getStationById(id)
            .onStart {
                try {
                    dao.upsert(api.getStation(id).toEntity())
                } catch (_: Exception) {
                    // Cache will serve; the ViewModel's refreshStation call surfaces errors.
                }
            }

    override suspend fun refreshStation(id: String) {
        dao.upsert(api.getStation(id).toEntity())
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
        address = address,
    )
}
