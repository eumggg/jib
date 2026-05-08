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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jib.app.auth.AuthViewModel
import com.jib.app.data.model.Station
import com.jib.app.data.remote.CheckInDto
import com.jib.app.data.remote.ReviewDto
import com.jib.app.ui.checkin.CheckInUiState
import com.jib.app.ui.checkin.CheckInViewModel
import com.jib.app.ui.photo.PhotoGallery
import com.jib.app.ui.report.ReportBottomSheet
import com.jib.app.ui.review.ReviewBottomSheet
import com.jib.app.ui.review.ReviewUiState
import com.jib.app.ui.review.ReviewViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    onBack: () -> Unit,
    viewModel: StationDetailViewModel = hiltViewModel(),
    checkInViewModel: CheckInViewModel = hiltViewModel(),
    reviewViewModel: ReviewViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checkInState by checkInViewModel.uiState.collectAsStateWithLifecycle()
    val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val coScope = rememberCoroutineScope()

    var showOverflow by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    val reviewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val station = uiState.station
    LaunchedEffect(station?.id) {
        station?.id?.let { id ->
            checkInViewModel.loadRecentCheckIns(id)
            reviewViewModel.loadReviews(id)
        }
    }
    LaunchedEffect(checkInState.justSubmitted) {
        if (checkInState.justSubmitted) {
            snackbar.showSnackbar("Checked in. Thanks!")
            checkInViewModel.consumeJustSubmitted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station?.name ?: "Station") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentUser != null && station != null) {
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Report issue") },
                                    onClick = {
                                        showOverflow = false
                                        showReportSheet = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            station != null -> StationDetailContent(
                station = station,
                checkInState = checkInState,
                reviewState = reviewState,
                isAuthed = currentUser != null,
                onCheckIn = { checkInViewModel.submitCheckIn(station.id) },
                onWriteReview = { showReviewSheet = true },
                onGetDirections = { launchDirections(context, station) },
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

    if (showReviewSheet && station != null) {
        ReviewBottomSheet(
            stationId = station.id,
            sheetState = reviewSheetState,
            onDismiss = {
                coScope.launch {
                    reviewSheetState.hide()
                    showReviewSheet = false
                }
            },
            onSubmitted = {
                showReviewSheet = false
                coScope.launch { snackbar.showSnackbar("Review posted. Thanks!") }
            },
            viewModel = reviewViewModel,
        )
    }
    if (showReportSheet && station != null) {
        ReportBottomSheet(
            stationId = station.id,
            sheetState = reportSheetState,
            onDismiss = {
                coScope.launch {
                    reportSheetState.hide()
                    showReportSheet = false
                }
            },
            onSubmitted = {
                showReportSheet = false
                coScope.launch { snackbar.showSnackbar("Report submitted. Thank you.") }
            },
        )
    }
}

@Composable
private fun StationDetailContent(
    station: Station,
    checkInState: CheckInUiState,
    reviewState: ReviewUiState,
    isAuthed: Boolean,
    onCheckIn: () -> Unit,
    onWriteReview: () -> Unit,
    onGetDirections: () -> Unit,
    padding: PaddingValues,
) {
    val now = System.currentTimeMillis()
    val onCooldown = checkInState.cooldownUntilMs > now
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvailabilityBadge(isAvailable = station.isAvailable)
        AddressLine(station = station)
        // Station-detail GET returns no avgRating (only the bbox endpoint does), so
        // fall back to the average of loaded reviews when the field is missing.
        val derivedAvg = station.avgRating
            ?: reviewState.reviews
                .map { it.rating }
                .takeIf { it.isNotEmpty() }
                ?.average()
        RatingRow(avgRating = derivedAvg, reviewCount = reviewState.reviews.size)
        ConnectorChips(types = station.connectorTypeList())
        StationFacts(station = station)

        PhotoGallery(stationId = station.id)

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onGetDirections, modifier = Modifier.weight(1f)) {
                Text("Get Directions")
            }
            FilledTonalButton(
                onClick = onCheckIn,
                enabled = isAuthed && !checkInState.isSubmitting && !onCooldown,
                modifier = Modifier.weight(1f),
            ) {
                if (checkInState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (onCooldown) "Checked in" else "Check in")
            }
        }

        OutlinedButton(
            onClick = onWriteReview,
            enabled = isAuthed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Write a review")
        }

        ReviewsSection(reviews = reviewState.reviews, isLoading = reviewState.isLoading)
        RecentCheckInsSection(checkIns = checkInState.recentCheckIns, isLoading = checkInState.isLoading)
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
private fun RatingRow(avgRating: Double?, reviewCount: Int) {
    if (avgRating == null && reviewCount == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300))
        Spacer(Modifier.width(6.dp))
        val ratingText = avgRating?.let { "%.1f".format(it) } ?: "—"
        Text(
            text = "$ratingText  ($reviewCount review${if (reviewCount == 1) "" else "s"})",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
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
                AssistChip(onClick = {}, label = { Text(connectorLabel(type)) })
            }
        }
    }
}

@Composable
private fun StationFacts(station: Station) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FactRow(label = "Power", value = station.powerKw?.let { "%.0f kW".format(it) } ?: "—")
        FactRow(label = "Network", value = station.networkOperator ?: "—")
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
private fun ReviewsSection(reviews: List<ReviewDto>, isLoading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Reviews",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            isLoading && reviews.isEmpty() -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            reviews.isEmpty() -> Text(
                text = "No reviews yet — be the first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> reviews.take(10).forEach { review ->
                ReviewRow(review)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReviewRow(review: ReviewDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = review.displayName?.takeIf { it.isNotBlank() } ?: "User",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "★".repeat(review.rating),
                color = Color(0xFFFFB300),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = review.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        review.body?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun RecentCheckInsSection(checkIns: List<CheckInDto>, isLoading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Recent check-ins",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            isLoading && checkIns.isEmpty() -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            checkIns.isEmpty() -> Text(
                text = "No recent check-ins.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> checkIns.take(5).forEach { c ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = c.createdAt.take(19).replace('T', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    c.comment?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
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
