package com.jib.app.data.places

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

interface PlacesRepository {
    suspend fun autocomplete(query: String, sessionToken: AutocompleteSessionToken): List<PlaceSuggestion>
    suspend fun fetchLatLng(placeId: String, sessionToken: AutocompleteSessionToken): LatLng?
}

@Singleton
class PlacesRepositoryImpl @Inject constructor(
    private val placesClient: PlacesClient?,
) : PlacesRepository {

    override suspend fun autocomplete(
        query: String,
        sessionToken: AutocompleteSessionToken,
    ): List<PlaceSuggestion> {
        val client = placesClient ?: return emptyList()
        if (query.isBlank()) return emptyList()
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()
        return runCatching {
            client.findAutocompletePredictions(request).await()
                .autocompletePredictions
                .map {
                    PlaceSuggestion(
                        placeId = it.placeId,
                        primaryText = it.getPrimaryText(null).toString(),
                        secondaryText = it.getSecondaryText(null).toString(),
                    )
                }
        }.getOrElse { emptyList() }
    }

    override suspend fun fetchLatLng(
        placeId: String,
        sessionToken: AutocompleteSessionToken,
    ): LatLng? {
        val client = placesClient ?: return null
        val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG))
            .setSessionToken(sessionToken)
            .build()
        return runCatching {
            client.fetchPlace(request).await().place.latLng
        }.getOrNull()
    }
}
