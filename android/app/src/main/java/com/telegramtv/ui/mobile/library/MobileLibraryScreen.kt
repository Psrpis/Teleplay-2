package com.telegramtv.ui.mobile.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.telegramtv.data.model.FileItem
import com.telegramtv.data.model.HistoryEntry
import com.telegramtv.data.model.MediaCollection
import com.telegramtv.ui.mobile.components.InputDialog
import com.telegramtv.ui.mobile.components.MediaDetailSheet
import com.telegramtv.ui.mobile.components.MediaPosterCard
import com.telegramtv.ui.mobile.components.MediaWideCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLibraryScreen(
    viewModel: MobileLibraryViewModel = hiltViewModel(),
    onPlayFile: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshCurrentTab()
        }
    }
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) pullToRefreshState.startRefresh()
        else pullToRefreshState.endRefresh()
    }

    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    var showBulkAddDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearContinueWatchingDialog by remember { mutableStateOf(false) }

    // Back handler for collection detail view
    BackHandler(enabled = uiState.selectedCollection != null) {
        viewModel.closeCollection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp) // Space for bottom nav
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.selectedCollection != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.closeCollection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.selectedCollection?.name ?: "Collection",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Quick action buttons based on tab
                    when (uiState.selectedTab) {
                        LibraryTab.CONTINUE_WATCHING -> {
                            if (uiState.continueWatching.isNotEmpty()) {
                                TextButton(onClick = { showClearContinueWatchingDialog = true }) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        LibraryTab.HISTORY -> {
                            if (uiState.history.isNotEmpty()) {
                                TextButton(onClick = { showClearHistoryDialog = true }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        LibraryTab.COLLECTIONS -> {
                            IconButton(onClick = { showCreateCollectionDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "New Collection", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Tabs / Segmented chips row (only when not in collection detail)
            if (uiState.selectedCollection == null) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {}
                ) {
                    LibraryTab.values().forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text(tab.title) },
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tab Content
            if (uiState.selectedCollection != null) {
                // Collection Content
                CollectionDetailContent(
                    collection = uiState.selectedCollection!!,
                    files = uiState.collectionFiles,
                    serverUrl = uiState.serverUrl,
                    onPlayFile = onPlayFile,
                    onFileClick = { viewModel.showFileDetail(it) },
                    onBulkAddClick = { showBulkAddDialog = true }
                )
            } else {
                when (uiState.selectedTab) {
                    LibraryTab.CONTINUE_WATCHING -> {
                        ContinueWatchingContent(
                            files = uiState.continueWatching,
                            serverUrl = uiState.serverUrl,
                            onPlayFile = onPlayFile,
                            onFileClick = { viewModel.showFileDetail(it) },
                            onRemove = { viewModel.removeContinueWatching(it) }
                        )
                    }
                    LibraryTab.RECENTLY_ADDED -> {
                        RecentFilesContent(
                            files = uiState.recentFiles,
                            serverUrl = uiState.serverUrl,
                            onPlayFile = onPlayFile,
                            onFileClick = { viewModel.showFileDetail(it) }
                        )
                    }
                    LibraryTab.FAVORITES -> {
                        FavoritesContent(
                            files = uiState.favorites,
                            serverUrl = uiState.serverUrl,
                            onPlayFile = onPlayFile,
                            onFileClick = { viewModel.showFileDetail(it) }
                        )
                    }
                    LibraryTab.HISTORY -> {
                        HistoryContent(
                            history = uiState.history,
                            serverUrl = uiState.serverUrl,
                            onPlayFile = onPlayFile,
                            onFileClick = { viewModel.showFileDetail(it) },
                            onDelete = { viewModel.deleteHistoryItem(it) }
                        )
                    }
                    LibraryTab.COLLECTIONS -> {
                        CollectionsListContent(
                            collections = uiState.collections,
                            onSelectCollection = { viewModel.selectCollection(it) },
                            onCreateClick = { showCreateCollectionDialog = true }
                        )
                    }
                }
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    // Media Detail Sheet
    uiState.selectedDetailFile?.let { detailFile ->
        MediaDetailSheet(
            file = detailFile,
            serverUrl = uiState.serverUrl,
            onDismiss = { viewModel.dismissFileDetail() },
            onPlay = {
                viewModel.dismissFileDetail()
                onPlayFile(detailFile.id)
            },
            onToggleFavorite = { isFav -> viewModel.toggleFavorite(detailFile, isFav) },
            onToggleWatched = { isWatched -> viewModel.toggleWatched(detailFile, isWatched) }
        )
    }

    // Dialogs
    if (showCreateCollectionDialog) {
        InputDialog(
            title = "New Collection",
            initialValue = "",
            onDismiss = { showCreateCollectionDialog = false },
            onConfirm = { name ->
                showCreateCollectionDialog = false
                if (name.isNotBlank()) viewModel.createCollection(name.trim())
            }
        )
    }

    if (showBulkAddDialog && uiState.selectedCollection != null) {
        InputDialog(
            title = "Add to Collection (Search Query)",
            initialValue = "",
            onDismiss = { showBulkAddDialog = false },
            onConfirm = { query ->
                showBulkAddDialog = false
                if (query.isNotBlank()) {
                    viewModel.bulkAddToCollection(uiState.selectedCollection!!.id, query.trim())
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear your entire watch history?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    viewModel.clearHistory()
                }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearContinueWatchingDialog) {
        AlertDialog(
            onDismissRequest = { showClearContinueWatchingDialog = false },
            title = { Text("Clear Continue Watching") },
            text = { Text("Are you sure you want to remove all items from Continue Watching?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearContinueWatchingDialog = false
                    viewModel.clearContinueWatching()
                }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearContinueWatchingDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ContinueWatchingContent(
    files: List<FileItem>,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onRemove: (Int) -> Unit
) {
    if (files.isEmpty()) {
        EmptyStateBox(message = "No items in Continue Watching")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaWideCard(
                    file = file,
                    serverUrl = serverUrl,
                    onResumeClick = { onPlayFile(file.id) },
                    onDetailClick = { onFileClick(file) },
                    onDeleteClick = { onRemove(file.id) }
                )
            }
        }
    }
}

@Composable
private fun RecentFilesContent(
    files: List<FileItem>,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    if (files.isEmpty()) {
        EmptyStateBox(message = "No recently added files")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaPosterCard(
                    file = file,
                    serverUrl = serverUrl,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesContent(
    files: List<FileItem>,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    if (files.isEmpty()) {
        EmptyStateBox(message = "No favorites added yet")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaPosterCard(
                    file = file,
                    serverUrl = serverUrl,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
private fun HistoryContent(
    history: List<HistoryEntry>,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (history.isEmpty()) {
        EmptyStateBox(message = "Watch history is empty")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.id }) { entry ->
                entry.file?.let { file ->
                    MediaWideCard(
                        file = file,
                        serverUrl = serverUrl,
                        progressLabel = "Watched ${entry.watchedAt.take(10)}",
                        onResumeClick = { onPlayFile(file.id) },
                        onDetailClick = { onFileClick(file) },
                        onDeleteClick = { onDelete(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionsListContent(
    collections: List<MediaCollection>,
    onSelectCollection: (MediaCollection) -> Unit,
    onCreateClick: () -> Unit
) {
    if (collections.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CollectionsBookmark,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Collections Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create collections to organize your movies and series",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Collection")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(collections, key = { it.id }) { collection ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCollection(collection) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            collection.description?.let { desc ->
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${collection.itemCount} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailContent(
    collection: MediaCollection,
    files: List<FileItem>,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onBulkAddClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${files.size} items in collection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(onClick = onBulkAddClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Items")
            }
        }

        if (files.isEmpty()) {
            EmptyStateBox(message = "This collection is empty. Tap 'Add Items' to add media.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    MediaPosterCard(
                        file = file,
                        serverUrl = serverUrl,
                        onClick = { onFileClick(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
