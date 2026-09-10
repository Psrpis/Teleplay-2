package com.telegramtv.ui.mobile.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramtv.data.model.FileItem
import com.telegramtv.data.model.HistoryEntry
import com.telegramtv.data.model.MediaCollection
import com.telegramtv.data.repository.FilesRepository
import com.telegramtv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val title: String) {
    CONTINUE_WATCHING("Continue"),
    RECENTLY_ADDED("Recent"),
    FAVORITES("Favorites"),
    HISTORY("History"),
    COLLECTIONS("Collections")
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.CONTINUE_WATCHING,
    val isLoading: Boolean = false,
    val serverUrl: String = "",
    val continueWatching: List<FileItem> = emptyList(),
    val recentFiles: List<FileItem> = emptyList(),
    val favorites: List<FileItem> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val collections: List<MediaCollection> = emptyList(),
    val selectedCollection: MediaCollection? = null,
    val collectionFiles: List<FileItem> = emptyList(),
    val selectedDetailFile: FileItem? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MobileLibraryViewModel @Inject constructor(
    private val filesRepository: FilesRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsRepository.getServerUrl()
            _uiState.update { it.copy(serverUrl = url) }
            loadAll()
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedCollection = null) }
        refreshCurrentTab()
    }

    fun refreshCurrentTab() {
        viewModelScope.launch {
            when (_uiState.value.selectedTab) {
                LibraryTab.CONTINUE_WATCHING -> loadContinueWatching()
                LibraryTab.RECENTLY_ADDED -> loadRecentFiles()
                LibraryTab.FAVORITES -> loadFavorites()
                LibraryTab.HISTORY -> loadHistory()
                LibraryTab.COLLECTIONS -> loadCollections()
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadContinueWatching()
            loadRecentFiles()
            loadFavorites()
            loadHistory()
            loadCollections()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadContinueWatching() {
        val result = filesRepository.getContinueWatchingWeb()
        _uiState.update { it.copy(continueWatching = result.getOrNull() ?: emptyList()) }
    }

    private suspend fun loadRecentFiles() {
        val result = filesRepository.getRecentFiles(limit = 40)
        _uiState.update { it.copy(recentFiles = result.getOrNull() ?: emptyList()) }
    }

    private suspend fun loadFavorites() {
        val result = filesRepository.getFavorites()
        _uiState.update { it.copy(favorites = result.getOrNull() ?: emptyList()) }
    }

    private suspend fun loadHistory() {
        val result = filesRepository.getHistory()
        _uiState.update { it.copy(history = result.getOrNull() ?: emptyList()) }
    }

    private suspend fun loadCollections() {
        val result = filesRepository.getCollections()
        _uiState.update { it.copy(collections = result.getOrNull() ?: emptyList()) }
    }

    fun removeContinueWatching(fileId: Int) {
        viewModelScope.launch {
            filesRepository.removeContinueWatching(fileId)
            loadContinueWatching()
        }
    }

    fun clearContinueWatching() {
        viewModelScope.launch {
            filesRepository.clearContinueWatching()
            _uiState.update { it.copy(continueWatching = emptyList()) }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            filesRepository.deleteHistoryItem(id)
            loadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            filesRepository.clearHistory()
            _uiState.update { it.copy(history = emptyList()) }
        }
    }

    fun createCollection(name: String, description: String? = null) {
        viewModelScope.launch {
            filesRepository.createCollection(name, description)
            loadCollections()
        }
    }

    fun selectCollection(collection: MediaCollection) {
        _uiState.update { it.copy(selectedCollection = collection, isLoading = true) }
        viewModelScope.launch {
            val result = filesRepository.getFiles(folderId = null)
            val allFiles = result.getOrNull()?.items ?: emptyList()
            val filtered = allFiles.filter { file -> collection.files.any { it.id == file.id } }
            _uiState.update { it.copy(collectionFiles = filtered, isLoading = false) }
        }
    }

    fun closeCollection() {
        _uiState.update { it.copy(selectedCollection = null, collectionFiles = emptyList()) }
    }

    fun bulkAddToCollection(collectionId: Int, query: String) {
        viewModelScope.launch {
            filesRepository.bulkAddToCollection(collectionId, query)
            loadCollections()
        }
    }

    fun showFileDetail(file: FileItem) {
        _uiState.update { it.copy(selectedDetailFile = file) }
    }

    fun dismissFileDetail() {
        _uiState.update { it.copy(selectedDetailFile = null) }
    }

    fun toggleFavorite(file: FileItem, isFav: Boolean) {
        viewModelScope.launch {
            filesRepository.toggleFavorite(file.id, isFav)
            // Refresh local state
            loadFavorites()
            if (_uiState.value.selectedDetailFile?.id == file.id) {
                _uiState.update { it.copy(selectedDetailFile = file.copy(isFavorite = isFav)) }
            }
        }
    }

    fun toggleWatched(file: FileItem, isWatched: Boolean) {
        viewModelScope.launch {
            val state = if (isWatched) "watched" else "unwatched"
            filesRepository.updateWatchedState(file.id, state)
            loadContinueWatching()
            loadHistory()
            if (_uiState.value.selectedDetailFile?.id == file.id) {
                _uiState.update { it.copy(selectedDetailFile = file.copy(watchedState = state)) }
            }
        }
    }
}
