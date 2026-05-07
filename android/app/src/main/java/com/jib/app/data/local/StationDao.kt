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

    @Upsert
    suspend fun upsertAll(stations: List<Station>)
}
