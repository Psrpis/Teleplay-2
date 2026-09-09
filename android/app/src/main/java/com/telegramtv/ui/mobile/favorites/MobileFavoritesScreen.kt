package com.telegramtv.ui.mobile.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telegramtv.data.model.FileItem
import com.telegramtv.data.repository.FilesRepository
import com.telegramtv.data.repository.SettingsRepository
import com.telegramtv.ui.theme.MobilePrimary
import com.telegramtv.ui.theme.MobileSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MobileFavoritesViewModel @Inject constructor(
    private val filesRepository: FilesRepository,
    private val settingsRepository: SettingsRepository
) : androidx.lifecycle.ViewModel() {
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files = _files.asStateFlow()
    private val _serverUrl = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()
    var loading by mutableStateOf(false)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            _serverUrl.value = settingsRepository.getServerUrl()
            _files.value = filesRepository.getFavorites().getOrNull() ?: emptyList()
            loading = false
        }
    }
}

@Composable
fun MobileFavoritesScreen(
    onPlayFile: (Int) -> Unit,
    viewModel: MobileFavoritesViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 16.dp)
        )
        if (viewModel.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MobilePrimary)
        }
        if (!viewModel.loading && files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your favorite media will appear here", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    FavoriteMediaRow(file, serverUrl) { onPlayFile(file.id) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteMediaRow(file: FileItem, serverUrl: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MobileSurface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(112.dp, 72.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.thumbnailUrl?.let { if (it.startsWith("http")) it else "$serverUrl$it" })
                    .crossfade(true).build(),
                contentDescription = file.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (file.thumbnailUrl == null) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MobilePrimary, MobileSurface))))
            }
            Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.fileName, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            file.formattedDuration?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
