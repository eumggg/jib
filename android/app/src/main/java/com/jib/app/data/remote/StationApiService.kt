package com.jib.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
    @SerializedName("address") val address: String? = null,
    @SerializedName("avgRating") val avgRating: Double? = null,
    @SerializedName("recentCheckInAt") val recentCheckInAt: String? = null,
)

data class StationsResponse(val stations: List<StationDto>)

data class CreateStationRequest(
    @SerializedName("idempotencyKey") val idempotencyKey: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("connectorTypes") val connectorTypes: List<String>,
    @SerializedName("powerKw") val powerKw: Double?,
    @SerializedName("networkOperator") val networkOperator: String?,
)

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

    @POST("stations")
    suspend fun createStation(@Body body: CreateStationRequest): StationDto
}
