package com.jib.app.ui.station

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jib.app.data.model.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    onBack: () -> Unit,
    viewModel: StationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.station?.name ?: "Station") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.station != null -> StationDetailContent(
                station = uiState.station!!,
                onGetDirections = { launchDirections(context, uiState.station!!) },
                padding = padding,
            )
            uiState.isLoading -> StationDetailSkeleton(padding)
            uiState.errorMessage != null -> StationDetailError(
                message = uiState.errorMessage!!,
                onRetry = viewModel::retry,
                padding = padding,
            )
        }
    }
}

@Composable
private fun StationDetailContent(
    station: Station,
    onGetDirections: () -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvailabilityBadge(isAvailable = station.isAvailable)

        AddressLine(station = station)

        ConnectorChips(types = station.connectorTypeList())

        StationFacts(station = station)

        PhotoCarouselPlaceholder()

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onGetDirections,
                modifier = Modifier.weight(1f),
            ) {
                Text("Get Directions")
            }
            FilledTonalButton(
                onClick = { /* Phase 2 stub */ },
                enabled = false,
                modifier = Modifier.weight(1f),
            ) {
                Text("Check in")
            }
        }

        OutlinedButton(
            onClick = { /* Phase 2 stub */ },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit a correction")
        }
    }
}

@Composable
private fun AvailabilityBadge(isAvailable: Boolean) {
    val color = if (isAvailable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isAvailable) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isAvailable) "Available" else "Currently unavailable",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AddressLine(station: Station) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = station.address ?: "%.5f, %.5f".format(station.latitude, station.longitude),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ConnectorChips(types: List<String>) {
    if (types.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Connectors (${types.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(types) { type ->
                AssistChip(
                    onClick = {},
                    label = { Text(connectorLabel(type)) },
                )
            }
        }
    }
}

@Composable
private fun StationFacts(station: Station) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FactRow(
            label = "Power",
            value = station.powerKw?.let { "%.0f kW".format(it) } ?: "—",
        )
        FactRow(
            label = "Network",
            value = station.networkOperator ?: "—",
        )
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PhotoCarouselPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Photos",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(List(3) { it }) {
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No photo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StationDetailSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(28.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(20.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(40.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(120.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun StationDetailError(
    message: String,
    onRetry: () -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Couldn't load this station.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun connectorLabel(type: String): String = when (type.uppercase()) {
    "CCS", "CCS1", "CCS2" -> "CCS"
    "CHADEMO" -> "CHAdeMO"
    "J1772" -> "J1772"
    "TESLA", "NACS" -> "Tesla"
    else -> type
}

private fun launchDirections(context: Context, station: Station) {
    val nav = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${station.latitude},${station.longitude}&mode=d"),
    ).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (nav.resolveActivity(context.packageManager) != null) {
        context.startActivity(nav)
        return
    }
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}"),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(fallback)
}
