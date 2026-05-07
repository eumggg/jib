package com.jib.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private const val BOUNDS_BUFFER = 0.2 // 20% viewport buffer (map-viewport-batching lens)

@Composable
fun MapScreen(
    onStationClick: (stationId: String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationPermissionGranted = granted }

    // Request coarse location on first composition if not already granted.
    // Play-Store-review-constraints lens: coarse only; fine is requested only when routing.
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 12f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            onMapLoaded = {
                // Trigger initial station load once the map is ready.
                val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
                if (bounds != null) {
                    val latBuffer = (bounds.northeast.latitude - bounds.southwest.latitude) * BOUNDS_BUFFER
                    val lngBuffer = (bounds.northeast.longitude - bounds.southwest.longitude) * BOUNDS_BUFFER
                    viewModel.fetchStations(
                        swLat = bounds.southwest.latitude - latBuffer,
                        swLng = bounds.southwest.longitude - lngBuffer,
                        neLat = bounds.northeast.latitude + latBuffer,
                        neLng = bounds.northeast.longitude + lngBuffer,
                    )
                }
            },
        ) {
            uiState.stations.forEach { station ->
                Marker(
                    state = MarkerState(LatLng(station.latitude, station.longitude)),
                    title = station.name,
                    onClick = {
                        onStationClick(station.id)
                        true
                    },
                )
            }
        }

        if (uiState.isOffline) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text("Offline — showing cached stations")
            }
        }
    }

    // Viewport-batching: re-fetch on every camera-idle event with buffered bounds.
    // This uses LaunchedEffect on cameraPositionState.isMoving to detect idle.
    val isMoving = cameraPositionState.isMoving
    LaunchedEffect(isMoving) {
        if (!isMoving) {
            val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
            val latBuffer = (bounds.northeast.latitude - bounds.southwest.latitude) * BOUNDS_BUFFER
            val lngBuffer = (bounds.northeast.longitude - bounds.southwest.longitude) * BOUNDS_BUFFER
            viewModel.fetchStations(
                swLat = bounds.southwest.latitude - latBuffer,
                swLng = bounds.southwest.longitude - lngBuffer,
                neLat = bounds.northeast.latitude + latBuffer,
                neLng = bounds.northeast.longitude + lngBuffer,
            )
        }
    }
}
