package com.telegramtv.ui.mobile.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telegramtv.data.model.FileItem
import com.telegramtv.data.model.MediaStats
import com.telegramtv.data.model.MediaTag
import com.telegramtv.ui.mobile.components.MediaDetailSheet
import com.telegramtv.ui.mobile.components.MediaPosterCard
import com.telegramtv.ui.mobile.components.MediaWideCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMoreScreen(
    viewModel: MobileMoreViewModel = hiltViewModel(),
    onPlayFile: (Int) -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var showServerDialog by remember { mutableStateOf(false) }
    var editedServerUrl by remember(uiState.serverUrl) { mutableStateOf(uiState.serverUrl) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Back handler to navigate back to More menu or list
    BackHandler(enabled = uiState.currentSubScreen != MoreSubScreen.MENU) {
        viewModel.navigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.currentSubScreen != MoreSubScreen.MENU) {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val title = when (uiState.currentSubScreen) {
                    MoreSubScreen.MENU -> "More"
                    MoreSubScreen.MOVIES -> "Movies"
                    MoreSubScreen.SERIES -> uiState.selectedSeries?.name ?: "Series"
                    MoreSubScreen.ACTORS -> uiState.selectedActor?.name ?: "Actors"
                    MoreSubScreen.STATISTICS -> "Statistics"
                    MoreSubScreen.SETTINGS -> "Settings"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Subscreen Contents
            when (uiState.currentSubScreen) {
                MoreSubScreen.MENU -> {
                    MoreMenuContent(
                        onNavigate = { viewModel.navigateTo(it) },
                        onSurpriseMe = { viewModel.rollSurpriseMe() },
                        isRolling = uiState.isRollingSurprise
                    )
                }
                MoreSubScreen.MOVIES -> {
                    MoviesContent(
                        movies = uiState.movies,
                        filter = uiState.movieFilter,
                        serverUrl = uiState.serverUrl,
                        onFilterChange = { viewModel.setMovieFilter(it) },
                        onFileClick = { viewModel.showFileDetail(it) }
                    )
                }
                MoreSubScreen.SERIES -> {
                    SeriesContent(
                        seriesList = uiState.seriesList,
                        selectedSeries = uiState.selectedSeries,
                        selectedSeason = uiState.selectedSeason,
                        serverUrl = uiState.serverUrl,
                        onSelectSeries = { viewModel.selectSeries(it) },
                        onSelectSeason = { viewModel.selectSeason(it) },
                        onPlayFile = onPlayFile,
                        onFileClick = { viewModel.showFileDetail(it) }
                    )
                }
                MoreSubScreen.ACTORS -> {
                    ActorsContent(
                        actors = uiState.actors,
                        selectedActor = uiState.selectedActor,
                        files = uiState.actorFiles,
                        serverUrl = uiState.serverUrl,
                        onSelectActor = { viewModel.selectActor(it) },
                        onPlayFile = onPlayFile,
                        onFileClick = { viewModel.showFileDetail(it) }
                    )
                }
                MoreSubScreen.STATISTICS -> {
                    StatisticsContent(
                        stats = uiState.stats,
                        serverUrl = uiState.serverUrl,
                        onPlayFile = onPlayFile,
                        onFileClick = { viewModel.showFileDetail(it) }
                    )
                }
                MoreSubScreen.SETTINGS -> {
                    SettingsContent(
                        serverUrl = uiState.serverUrl,
                        userName = uiState.userName,
                        onEditServer = { showServerDialog = true },
                        onLogoutClick = { showLogoutDialog = true }
                    )
                }
            }
        }

        // Surprise Me Dialog
        uiState.surpriseItem?.let { surprise ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSurprise() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Surprise Pick!")
                    }
                },
                text = {
                    Column {
                        val poster = (surprise.effectiveBackdropUrl ?: surprise.effectivePosterUrl)?.let { url ->
                            if (url.startsWith("http://") || url.startsWith("https://")) url
                            else "${uiState.serverUrl.trimEnd('/')}/$url"
                        }
                        if (poster != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(poster)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = surprise.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = surprise.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        surprise.metadata?.overview?.let { overview ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissSurprise()
                        onPlayFile(surprise.id)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rollSurpriseMe() }) {
                        Text("Roll Again")
                    }
                }
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

        // Server URL Dialog
        if (showServerDialog) {
            AlertDialog(
                onDismissRequest = { showServerDialog = false },
                title = { Text("Server URL") },
                text = {
                    OutlinedTextField(
                        value = editedServerUrl,
                        onValueChange = { editedServerUrl = it },
                        singleLine = true,
                        label = { Text("TelePlay Server Address") },
                        placeholder = { Text("http://192.168.1.100:8000") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val normalized = editedServerUrl.trim().trimEnd('/')
                        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                            viewModel.saveServerUrl(normalized)
                            showServerDialog = false
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showServerDialog = false }) { Text("Cancel") } }
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Sign Out") },
                text = { Text("Are you sure you want to sign out of TelePlay?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogout)
                    }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun MoreMenuContent(
    onNavigate: (MoreSubScreen) -> Unit,
    onSurpriseMe: () -> Unit,
    isRolling: Boolean
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MoreMenuItem(
                icon = Icons.Outlined.Movie,
                title = "Movies",
                subtitle = "Browse all movies and films",
                onClick = { onNavigate(MoreSubScreen.MOVIES) }
            )
        }

        item {
            MoreMenuItem(
                icon = Icons.Outlined.Tv,
                title = "Series",
                subtitle = "TV shows, seasons, and episode guide",
                onClick = { onNavigate(MoreSubScreen.SERIES) }
            )
        }

        item {
            MoreMenuItem(
                icon = Icons.Default.Person,
                title = "Actors",
                subtitle = "Explore media by cast and crew",
                onClick = { onNavigate(MoreSubScreen.ACTORS) }
            )
        }

        item {
            MoreMenuItem(
                icon = Icons.Default.BarChart,
                title = "Statistics",
                subtitle = "View your watch time, count and activity",
                onClick = { onNavigate(MoreSubScreen.STATISTICS) }
            )
        }

        item {
            // Surprise Me Card with distinct highlight
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSurpriseMe)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRolling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Surprise Me",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Can't decide? Pick a random movie to watch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Icon(
                        Icons.Default.Casino,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            MoreMenuItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "Server connection, preferences and account",
                onClick = { onNavigate(MoreSubScreen.SETTINGS) }
            )
        }
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun MoviesContent(
    movies: List<FileItem>,
    filter: String,
    serverUrl: String,
    onFilterChange: (String) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    val filteredMovies = when (filter) {
        "UNWATCHED" -> movies.filter { it.watchedState != "watched" }
        "WATCHED" -> movies.filter { it.watchedState == "watched" }
        else -> movies
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All", "UNWATCHED" to "Unwatched", "WATCHED" to "Watched").forEach { (key, label) ->
                FilterChip(
                    selected = filter == key,
                    onClick = { onFilterChange(key) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        if (filteredMovies.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No movies found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredMovies, key = { it.id }) { movie ->
                    MediaPosterCard(
                        file = movie,
                        serverUrl = serverUrl,
                        onClick = { onFileClick(movie) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesContent(
    seriesList: List<SeriesInfo>,
    selectedSeries: SeriesInfo?,
    selectedSeason: Int,
    serverUrl: String,
    onSelectSeries: (SeriesInfo) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    if (selectedSeries == null) {
        // List all TV Series
        if (seriesList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No TV series found in library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(seriesList) { series ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSeries(series) }
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val poster = series.posterUrl?.let { url ->
                                    if (url.startsWith("http://") || url.startsWith("https://")) url
                                    else "${serverUrl.trimEnd('/')}/$url"
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(poster)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = series.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = "${series.totalEpisodes} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Series Detail: Seasons & Episodes
        Column(modifier = Modifier.fillMaxSize()) {
            // Season Selector Chips
            if (selectedSeries.seasons.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedSeries.seasons.keys.sorted().forEach { seasonNum ->
                        FilterChip(
                            selected = selectedSeason == seasonNum,
                            onClick = { onSelectSeason(seasonNum) },
                            label = { Text("Season $seasonNum") },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            val episodes = selectedSeries.seasons[selectedSeason] ?: emptyList()
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(episodes, key = { it.id }) { ep ->
                    MediaWideCard(
                        file = ep,
                        serverUrl = serverUrl,
                        onResumeClick = { onPlayFile(ep.id) },
                        onDetailClick = { onFileClick(ep) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActorsContent(
    actors: List<MediaTag>,
    selectedActor: MediaTag?,
    files: List<FileItem>,
    serverUrl: String,
    onSelectActor: (MediaTag) -> Unit,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    if (selectedActor == null) {
        if (actors.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No actors found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(actors, key = { it.id }) { actor ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectActor(actor) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = actor.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${actor.fileCount} films / series",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else {
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files for ${selectedActor.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun StatisticsContent(
    stats: MediaStats?,
    serverUrl: String,
    onPlayFile: (Int) -> Unit,
    onFileClick: (FileItem) -> Unit
) {
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats 2x2 Grid Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Total Watched",
                        value = stats.totalWatched.toString(),
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Movies Watched",
                        value = stats.moviesWatched.toString(),
                        icon = Icons.Outlined.Movie,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Episodes Watched",
                        value = stats.episodesWatched.toString(),
                        icon = Icons.Outlined.Tv,
                        modifier = Modifier.weight(1f)
                    )
                    val hours = stats.totalWatchTime / 3600
                    val minutes = (stats.totalWatchTime % 3600) / 60
                    val watchTimeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    StatCard(
                        title = "Watch Time",
                        value = watchTimeText,
                        icon = Icons.Default.AccessTime,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Activity Section
            if (stats.recentActivity.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(stats.recentActivity) { file ->
                    MediaWideCard(
                        file = file,
                        serverUrl = serverUrl,
                        onResumeClick = { onPlayFile(file.id) },
                        onDetailClick = { onFileClick(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsContent(
    serverUrl: String,
    userName: String?,
    onEditServer: () -> Unit,
    onLogoutClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // User Profile Header
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = userName ?: "User",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Connected via Telegram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Connection",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            MoreMenuItem(
                icon = Icons.Default.Dns,
                title = "Server URL",
                subtitle = if (serverUrl.isNotBlank()) serverUrl else "Tap to configure server",
                onClick = onEditServer
            )
        }

        item {
            Text(
                text = "Account & Actions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            MoreMenuItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Sign Out",
                subtitle = "Sign out of your Telegram account",
                onClick = onLogoutClick
            )
        }
    }
}
