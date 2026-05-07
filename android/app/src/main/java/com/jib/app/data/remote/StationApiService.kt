package com.jib.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class StationDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("connectorTypes") val connectorTypes: List<String>,
    @SerializedName("powerKw") val powerKw: Double?,
    @SerializedName("networkOperator") val networkOperator: String?,
    @SerializedName("isAvailable") val isAvailable: Boolean,
)

data class StationsResponse(val stations: List<StationDto>)

interface StationApiService {
    @GET("stations")
    suspend fun getStations(
        @Query("swLat") swLat: Double,
        @Query("swLng") swLng: Double,
        @Query("neLat") neLat: Double,
        @Query("neLng") neLng: Double,
        @Query("connectorType") connectorType: String? = null,
    ): StationsResponse

    @GET("stations/{id}")
    suspend fun getStation(@Path("id") id: String): StationDto
}
