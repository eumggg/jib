package com.jib.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jib.app.data.remote.UserActivityItem
import kotlin.math.absoluteValue

private val TABS = listOf("Stations", "Check-ins", "Reviews", "Photos")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onStationClick: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProfileHeader(
                displayName = uiState.user?.displayName,
                email = uiState.user?.email ?: uiState.firebaseEmail,
                photoUrl = uiState.user?.photoUrl ?: uiState.firebasePhotoUrl,
                isSavingName = uiState.isSavingName,
                onSaveDisplayName = viewModel::saveDisplayName,
            )

            HorizontalDivider()

            TabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) },
                    )
                }
            }

            val items = when (selectedTab) {
                0 -> uiState.activity.stations
                1 -> uiState.activity.checkIns
                2 -> uiState.activity.reviews
                3 -> uiState.activity.photos
                else -> emptyList()
            }

            ActivityList(
                items = items,
                isLoading = uiState.isLoading,
                onItemClick = onStationClick,
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String?,
    email: String?,
    photoUrl: String?,
    isSavingName: Boolean,
    onSaveDisplayName: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(displayName) { mutableStateOf(displayName.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(photoUrl = photoUrl, fallbackName = displayName ?: email)

        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.width(220.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onSaveDisplayName(draft)
                        editing = false
                    },
                    enabled = !isSavingName && draft.isNotBlank(),
                ) {
                    if (isSavingName) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "Save name")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.clickable { editing = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName ?: "Add a display name",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit display name",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!email.isNullOrBlank()) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Avatar(photoUrl: String?, fallbackName: String?) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(initialsColor(fallbackName)),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials(fallbackName),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActivityList(
    items: List<UserActivityItem>,
    isLoading: Boolean,
    onItemClick: (String) -> Unit,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Nothing here yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item.stationId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = item.stationName ?: "Station",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val secondary = listOfNotNull(
                    item.rating?.let { "$it ★" },
                    item.body ?: item.comment,
                    formatRelative(item.createdAt),
                ).joinToString(" • ")
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

private fun initials(name: String?): String {
    val src = name?.trim().orEmpty()
    if (src.isEmpty()) return "?"
    val parts = src.split(" ", "@").filter { it.isNotBlank() }
    return parts.take(2).map { it.first().uppercaseChar() }.joinToString("")
}

private fun initialsColor(name: String?): Color {
    val palette = listOf(
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFEF6C00),
        Color(0xFF8E24AA),
        Color(0xFFD81B60),
        Color(0xFF00897B),
    )
    val key = name?.hashCode()?.absoluteValue ?: 0
    return palette[key % palette.size]
}

// Cheap relative-time formatter — no need to pull a date library for "5m ago".
private fun formatRelative(iso: String): String {
    return iso.take(10) // YYYY-MM-DD as a safe minimal display
}
