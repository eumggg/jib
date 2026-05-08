package com.jib.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jib.app.data.model.Station
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query(
        "SELECT * FROM stations WHERE latitude BETWEEN :swLat AND :neLat AND longitude BETWEEN :swLng AND :neLng"
    )
    fun getStationsInBounds(swLat: Double, swLng: Double, neLat: Double, neLng: Double): Flow<List<Station>>

    /**
     * Bounding-box query that also filters on the JSON-encoded connectorTypes string.
     * The `LIKE` matches the quoted wire value (e.g. `"CCS"`) so a substring like
     * `CCS` inside `CCS_COMBO` doesn't false-positive.
     */
    @Query(
        "SELECT * FROM stations " +
            "WHERE latitude BETWEEN :swLat AND :neLat " +
            "AND longitude BETWEEN :swLng AND :neLng " +
            "AND connectorTypes LIKE '%\"' || :connectorType || '\"%'"
    )
    fun getStationsInBoundsByConnector(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
        connectorType: String,
    ): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    fun getStationById(id: String): Flow<Station?>

    @Upsert
    suspend fun upsert(station: Station)

    @Upsert
    suspend fun upsertAll(stations: List<Station>)
}
