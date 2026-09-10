package com.telegramtv.ui.mobile.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.telegramtv.data.model.FileItem
import com.telegramtv.ui.mobile.components.MediaDetailSheet
import com.telegramtv.ui.mobile.components.MediaPosterCard
import com.telegramtv.ui.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayFile: (Int) -> Unit,
    onGoToFolder: ((Int, String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedDetailFile by remember { mutableStateOf<FileItem?>(null) }

    val filteredResults = remember(uiState.results, uiState.activeFilter) {
        when (uiState.activeFilter) {
            "MOVIES" -> uiState.results.filter {
                it.metadata?.mediaType == "movie" || (it.metadata?.mediaType != "episode" && it.fileType == "video")
            }
            "SERIES" -> uiState.results.filter {
                it.metadata?.mediaType == "episode" || it.metadata?.mediaType == "tv" || it.metadata?.season != null
            }
            "ACTORS" -> uiState.results.filter {
                it.metadata?.cast?.isNotEmpty() == true
            }
            else -> uiState.results
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("Search movies, series, files...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    if (uiState.query.isNotBlank()) viewModel.addRecentQuery(uiState.query)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All", "MOVIES" to "Movies", "SERIES" to "Series", "ACTORS" to "Actors").forEach { (filterKey, label) ->
                    FilterChip(
                        selected = uiState.activeFilter == filterKey,
                        onClick = { viewModel.setActiveFilter(filterKey) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Body Content
        if (uiState.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (uiState.query.isEmpty()) {
            // Recent Searches View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.recentQueries) { query ->
                        SuggestionChip(
                            onClick = {
                                viewModel.onQueryChange(query)
                                keyboardController?.hide()
                            },
                            label = { Text(query) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Find movies, series, actors or files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No results found for \"${uiState.query}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Results Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredResults, key = { it.id }) { file ->
                    MediaPosterCard(
                        file = file,
                        serverUrl = uiState.serverUrl,
                        onClick = { selectedDetailFile = file }
                    )
                }
            }
        }
    }

    // Media Detail Sheet
    selectedDetailFile?.let { detailFile ->
        MediaDetailSheet(
            file = detailFile,
            serverUrl = uiState.serverUrl,
            onDismiss = { selectedDetailFile = null },
            onPlay = {
                selectedDetailFile = null
                onPlayFile(detailFile.id)
            },
            onToggleFavorite = { isFav ->
                // Update local state if needed
                selectedDetailFile = detailFile.copy(isFavorite = isFav)
            },
            onToggleWatched = { isWatched ->
                selectedDetailFile = detailFile.copy(watchedState = if (isWatched) "watched" else "unwatched")
            }
        )
    }
}
