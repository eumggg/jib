package com.jib.app.data.repository

import com.jib.app.data.model.Station
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    /**
     * Bounding-box list, optionally filtered to stations whose connectorTypes
     * include `connectorType` (wire value, e.g. "CCS"). When null, no filter
     * is applied either client-side or server-side.
     */
    fun getStations(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
        connectorType: String? = null,
    ): Flow<List<Station>>

    /**
     * Offline-first: emits the cached row immediately (or null if absent), then
     * upserts the remote response so the same Flow emits the fresh data. The
     * caller decides how to surface the loading/error states.
     */
    fun getStation(id: String): Flow<Station?>

    /** Force a network refresh for a single station; throws on network/API errors. */
    suspend fun refreshStation(id: String)

    /**
     * Crowdsourced station submission. Caller passes a stable client UUID as
     * `idempotencyKey` so retries dedup server-side. The created Station is
     * also upserted into Room so the next viewport flow emission shows the
     * new marker on the map.
     */
    suspend fun submitStation(
        idempotencyKey: String,
        name: String,
        latitude: Double,
        longitude: Double,
        connectorTypes: List<String>,
        powerKw: Double?,
        networkOperator: String?,
    ): Result<Station>
}
