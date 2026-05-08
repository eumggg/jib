package com.jib.app.ui.submit

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

private val CONNECTOR_OPTIONS = listOf("CCS", "CHAdeMO", "J1772", "Tesla")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitStationScreen(
    onClose: () -> Unit,
    onSubmitted: (stationId: String) -> Unit,
    viewModel: SubmitStationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Watch for terminal submission outcomes and notify the host.
    LaunchedEffect(state.result) {
        when (val r = state.result) {
            is SubmitResult.Success -> {
                Toast.makeText(context, "Station submitted", Toast.LENGTH_SHORT).show()
                viewModel.acknowledgeResult()
                onSubmitted(r.stationId)
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Station") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step == SubmitStep.Location) onClose() else viewModel.back()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StepIndicator(step = state.step)
            when (state.step) {
                SubmitStep.Location -> {
                    val outerScope = rememberCoroutineScope()
                    LocationStep(
                        state = state,
                        onPick = { lat, lng ->
                            viewModel.setLocation(lat, lng)
                            outerScope.launch {
                                val addr = reverseGeocode(context, lat, lng)
                                if (addr != null) viewModel.setAddress(addr)
                            }
                        },
                        onNext = { viewModel.advance() },
                    )
                }
                SubmitStep.Details -> DetailsStep(
                    state = state,
                    onName = viewModel::setName,
                    onToggleConnector = viewModel::toggleConnector,
                    onPowerKw = viewModel::setPowerKw,
                    onNetwork = viewModel::setNetworkOperator,
                    onAddress = viewModel::setAddress,
                    onNext = { viewModel.advance() },
                )
                SubmitStep.Review -> ReviewStep(
                    state = state,
                    onSubmit = viewModel::submit,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: SubmitStep) {
    val progress = when (step) {
        SubmitStep.Location -> 1 / 3f
        SubmitStep.Details -> 2 / 3f
        SubmitStep.Review -> 1f
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LocationStep(
    state: SubmitStationUiState,
    onPick: (Double, Double) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = LatLng(state.latitude ?: 37.7749, state.longitude ?: -122.4194)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, 15f)
    }
    val markerState = rememberMarkerState()

    // Keep marker position in sync with viewmodel-tracked picked location.
    LaunchedEffect(state.latitude, state.longitude) {
        if (state.latitude != null && state.longitude != null) {
            markerState.position = LatLng(state.latitude, state.longitude)
        }
    }
    // Drag-end-sync lens: when the user drags the marker, push the new position back into the viewmodel.
    LaunchedEffect(markerState.position) {
        val pos = markerState.position
        if (state.latitude != null && state.longitude != null &&
            (pos.latitude != state.latitude || pos.longitude != state.longitude)) {
            onPick(pos.latitude, pos.longitude)
        }
    }

    var locationGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
        if (granted) scope.launch { useCurrentLocation(context, onPick, cameraPositionState) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationGranted),
            onMapClick = { latLng -> onPick(latLng.latitude, latLng.longitude) },
        ) {
            if (state.latitude != null && state.longitude != null) {
                Marker(
                    state = markerState,
                    draggable = true,
                    title = "Pinned location",
                )
            }
        }

        FloatingActionButton(
            onClick = {
                if (locationGranted) {
                    scope.launch { useCurrentLocation(context, onPick, cameraPositionState) }
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Use my location")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (state.locationError) {
                Text(
                    "Tap the map or use your location to pin the station.",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.latitude != null && state.longitude != null) {
                Text(
                    String.format(
                        Locale.US,
                        "Pinned: %.5f, %.5f",
                        state.latitude,
                        state.longitude,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onNext,
                enabled = state.latitude != null && state.longitude != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Next") }
        }
    }
}

@Composable
private fun DetailsStep(
    state: SubmitStationUiState,
    onName: (String) -> Unit,
    onToggleConnector: (String) -> Unit,
    onPowerKw: (String) -> Unit,
    onNetwork: (String) -> Unit,
    onAddress: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text("Station name *") },
            isError = state.nameError,
            supportingText = if (state.nameError) {
                { Text("Required") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.address,
            onValueChange = onAddress,
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Connector types", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CONNECTOR_OPTIONS.forEach { type ->
                FilterChip(
                    selected = state.connectorTypes.contains(type),
                    onClick = { onToggleConnector(type) },
                    label = { Text(type) },
                )
            }
        }

        OutlinedTextField(
            value = state.powerKw,
            onValueChange = onPowerKw,
            label = { Text("Power level (kW)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.networkOperator,
            onValueChange = onNetwork,
            label = { Text("Network (e.g. ChargePoint)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Review") }
    }
}

@Composable
private fun ReviewStep(
    state: SubmitStationUiState,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state.name, style = MaterialTheme.typography.headlineSmall)
        if (state.address.isNotBlank()) Text(state.address)
        Text(
            String.format(
                Locale.US, "Location: %.5f, %.5f",
                state.latitude ?: 0.0, state.longitude ?: 0.0,
            ),
        )
        if (state.connectorTypes.isNotEmpty()) {
            Text("Connectors: ${state.connectorTypes.joinToString(", ")}")
        }
        state.powerKw.toDoubleOrNull()?.let {
            Text("Power: ${it} kW")
        }
        if (state.networkOperator.isNotBlank()) Text("Network: ${state.networkOperator}")

        Spacer(Modifier.height(16.dp))

        when (val r = state.result) {
            is SubmitResult.Submitting -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            is SubmitResult.Error -> {
                Text(r.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry") }
                }
            }
            else -> {
                Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("Submit") }
            }
        }
    }
}

private suspend fun useCurrentLocation(
    context: android.content.Context,
    onPick: (Double, Double) -> Unit,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return
    try {
        @Suppress("MissingPermission")
        val location = LocationServices.getFusedLocationProviderClient(context)
            .lastLocation
            .await() ?: return
        onPick(location.latitude, location.longitude)
        cameraPositionState.position = CameraPosition.fromLatLngZoom(
            LatLng(location.latitude, location.longitude), 15f,
        )
    } catch (_: SecurityException) {
        // Permission was revoked between the check and the call. Silent — caller already prompts.
    }
}

private suspend fun reverseGeocode(
    context: android.content.Context,
    lat: Double,
    lng: Double,
): String? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                cont.resumeWith(Result.success(addresses.firstOrNull()?.getAddressLine(0)))
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
            }.getOrNull()
        }
    }
}
