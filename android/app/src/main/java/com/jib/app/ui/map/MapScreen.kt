package com.jib.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jib.app.auth.AuthViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.jib.app.ui.search.SearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val BOUNDS_BUFFER = 0.2 // 20% viewport buffer (map-viewport-batching lens)
private const val PLACE_ZOOM = 14f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onStationClick: (stationId: String) -> Unit,
    onAddStation: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationPermissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 12f)
    }

    // Recenter the map when the user picks a Places suggestion. The next
    // camera-idle event then triggers a viewport refetch — same path as a pan.
    LaunchedEffect(Unit) {
        searchViewModel.placeSelected.collectLatest { latLng ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, PLACE_ZOOM)
        }
    }

    val sheetState = rememberModalBottomSheetState()
    val sheetScope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
            contentPadding = PaddingValues(top = 96.dp),
            onMapLoaded = {
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

        // Search bar + autocomplete dropdown + active filter chip pinned to top.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            SearchBarRow(
                query = searchState.query,
                onQueryChange = searchViewModel::onQueryChanged,
                onClear = searchViewModel::clearQuery,
                onOpenFilter = { showFilterSheet = true },
                hasActiveFilter = uiState.selectedConnector != null,
            )

            if (searchState.isExpanded && searchState.suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    LazyColumn {
                        items(searchState.suggestions, key = { it.placeId }) { suggestion ->
                            SuggestionRow(
                                primary = suggestion.primaryText,
                                secondary = suggestion.secondaryText,
                                onClick = { searchViewModel.onSuggestionSelected(suggestion) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            uiState.selectedConnector?.let { connector ->
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { showFilterSheet = true },
                        label = { Text(connector.displayName) },
                        leadingIcon = {
                            Icon(Icons.Filled.FilterList, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.setConnectorFilter(null) },
                                modifier = Modifier,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear connector filter",
                                )
                            }
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
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

        // Empty state for filter that returns no stations in the current viewport.
        // We only surface this when a filter is active so an empty unfiltered map
        // (e.g. ocean viewport) doesn't show a confusing "no matches" toast.
        if (uiState.isEmptyResult && uiState.selectedConnector != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(
                    "No ${uiState.selectedConnector!!.displayName} stations in this area",
                )
            }
        }

        if (currentUser != null) {
            ExtendedFloatingActionButton(
                onClick = onAddStation,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add station") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            sheetState = sheetState,
            initial = uiState.selectedConnector,
            onDismiss = {
                sheetScope.launch {
                    sheetState.hide()
                    showFilterSheet = false
                }
            },
            onApply = { picked ->
                viewModel.setConnectorFilter(picked)
                sheetScope.launch {
                    sheetState.hide()
                    showFilterSheet = false
                }
            },
        )
    }

    // Viewport-batching: re-fetch on every camera-idle event with buffered bounds.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenFilter: () -> Unit,
    hasActiveFilter: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                ),
            placeholder = { Text("Search address or place") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        FilledIconButton(onClick = onOpenFilter) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = if (hasActiveFilter) "Filter (active)" else "Filter",
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    primary: String,
    secondary: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = primary, style = MaterialTheme.typography.bodyLarge)
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
