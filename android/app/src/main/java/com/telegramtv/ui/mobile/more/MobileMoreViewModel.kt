package com.telegramtv.ui.mobile.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramtv.data.model.FileItem
import com.telegramtv.data.model.MediaStats
import com.telegramtv.data.model.MediaTag
import com.telegramtv.data.repository.AuthRepository
import com.telegramtv.data.repository.FilesRepository
import com.telegramtv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MoreSubScreen {
    MENU,
    MOVIES,
    SERIES,
    ACTORS,
    STATISTICS,
    SETTINGS
}

data class SeriesInfo(
    val name: String,
    val totalEpisodes: Int,
    val seasons: Map<Int, List<FileItem>>,
    val posterUrl: String? = null
)

data class MoreUiState(
    val currentSubScreen: MoreSubScreen = MoreSubScreen.MENU,
    val isLoading: Boolean = false,
    val serverUrl: String = "",
    val userName: String? = null,

    // Movies
    val movies: List<FileItem> = emptyList(),
    val movieFilter: String = "ALL", // ALL, UNWATCHED, WATCHED

    // Series
    val seriesList: List<SeriesInfo> = emptyList(),
    val selectedSeries: SeriesInfo? = null,
    val selectedSeason: Int = 1,

    // Actors
    val actors: List<MediaTag> = emptyList(),
    val selectedActor: MediaTag? = null,
    val actorFiles: List<FileItem> = emptyList(),

    // Statistics
    val stats: MediaStats? = null,

    // Surprise Me
    val surpriseItem: FileItem? = null,
    val isRollingSurprise: Boolean = false,

    // Detail Sheet
    val selectedDetailFile: FileItem? = null,

    val errorMessage: String? = null
)

@HiltViewModel
class MobileMoreViewModel @Inject constructor(
    private val filesRepository: FilesRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsRepository.getServerUrl()
            val user = authRepository.userName
            _uiState.update { it.copy(serverUrl = url) }
            authRepository.userName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }
    }

    fun navigateTo(subScreen: MoreSubScreen) {
        _uiState.update {
            it.copy(
                currentSubScreen = subScreen,
                selectedSeries = null,
                selectedActor = null,
                actorFiles = emptyList()
            )
        }
        when (subScreen) {
            MoreSubScreen.MOVIES -> loadMovies()
            MoreSubScreen.SERIES -> loadSeries()
            MoreSubScreen.ACTORS -> loadActors()
            MoreSubScreen.STATISTICS -> loadStats()
            else -> {}
        }
    }

    fun navigateBack() {
        if (_uiState.value.selectedSeries != null) {
            _uiState.update { it.copy(selectedSeries = null) }
        } else if (_uiState.value.selectedActor != null) {
            _uiState.update { it.copy(selectedActor = null, actorFiles = emptyList()) }
        } else {
            _uiState.update { it.copy(currentSubScreen = MoreSubScreen.MENU) }
        }
    }

    fun loadMovies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = filesRepository.getFiles(folderId = null, fileType = "video")
            val allVideos = result.getOrNull() ?: emptyList()
            // Filter out files that are explicitly episodes if mediaType == "episode"
            val moviesList = allVideos.filter {
                it.metadata?.mediaType != "episode" && it.metadata?.mediaType != "tv"
            }
            _uiState.update { it.copy(movies = moviesList, isLoading = false) }
        }
    }

    fun setMovieFilter(filter: String) {
        _uiState.update { it.copy(movieFilter = filter) }
    }

    fun loadSeries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = filesRepository.getFiles(folderId = null, fileType = "video")
            val allVideos = result.getOrNull() ?: emptyList()

            // Group by series: check metadata.title (or parse file name / metadata.mediaType)
            val seriesMap = mutableMapOf<String, MutableList<FileItem>>()
            for (file in allVideos) {
                val seriesName = file.metadata?.let { meta ->
                    if (meta.mediaType == "episode" || meta.season != null) {
                        meta.originalTitle ?: meta.title?.substringBefore(" S0") ?: meta.title
                    } else null
                } ?: run {
                    // Fallback parse e.g. "Breaking Bad S01E01"
                    val name = file.fileName
                    if (Regex("S\\d{1,2}E\\d{1,2}", RegexOption.IGNORE_CASE).containsMatchIn(name)) {
                        name.substringBefore("S").trim().trimEnd('.', '-', '_', ' ')
                    } else null
                }

                if (seriesName != null && seriesName.isNotBlank()) {
                    seriesMap.getOrPut(seriesName) { mutableListOf() }.add(file)
                }
            }

            val seriesInfoList = seriesMap.map { (name, episodes) ->
                val seasonsGrouped = episodes.groupBy { it.metadata?.season ?: 1 }
                val poster = episodes.firstOrNull()?.effectivePosterUrl
                SeriesInfo(
                    name = name,
                    totalEpisodes = episodes.size,
                    seasons = seasonsGrouped,
                    posterUrl = poster
                )
            }.sortedBy { it.name }

            _uiState.update { it.copy(seriesList = seriesInfoList, isLoading = false) }
        }
    }

    fun selectSeries(series: SeriesInfo) {
        val initialSeason = series.seasons.keys.minOrNull() ?: 1
        _uiState.update { it.copy(selectedSeries = series, selectedSeason = initialSeason) }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season) }
    }

    fun loadActors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = filesRepository.getMediaTags(kind = "actor")
            val tags = result.getOrNull() ?: emptyList()
            _uiState.update { it.copy(actors = tags, isLoading = false) }
        }
    }

    fun selectActor(actor: MediaTag) {
        _uiState.update { it.copy(selectedActor = actor, isLoading = true) }
        viewModelScope.launch {
            val result = filesRepository.getFiles(folderId = null)
            val allFiles = result.getOrNull() ?: emptyList()
            val filtered = allFiles.filter { file ->
                file.metadata?.cast?.any { it.equals(actor.name, ignoreCase = true) } == true ||
                file.tags.any { it.equals(actor.name, ignoreCase = true) }
            }
            _uiState.update { it.copy(actorFiles = filtered, isLoading = false) }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = filesRepository.getMediaStats()
            _uiState.update { it.copy(stats = result.getOrNull(), isLoading = false) }
        }
    }

    fun rollSurpriseMe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRollingSurprise = true) }
            val result = filesRepository.getSurpriseMe()
            _uiState.update {
                it.copy(
                    surpriseItem = result.getOrNull(),
                    isRollingSurprise = false
                )
            }
        }
    }

    fun dismissSurprise() {
        _uiState.update { it.copy(surpriseItem = null) }
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
            if (_uiState.value.selectedDetailFile?.id == file.id) {
                _uiState.update { it.copy(selectedDetailFile = file.copy(isFavorite = isFav)) }
            }
        }
    }

    fun toggleWatched(file: FileItem, isWatched: Boolean) {
        viewModelScope.launch {
            val state = if (isWatched) "watched" else "unwatched"
            filesRepository.updateWatchedState(file.id, state)
            if (_uiState.value.selectedDetailFile?.id == file.id) {
                _uiState.update { it.copy(selectedDetailFile = file.copy(watchedState = state)) }
            }
            if (_uiState.value.currentSubScreen == MoreSubScreen.MOVIES) {
                loadMovies()
            }
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setServerUrl(url)
            _uiState.update { it.copy(serverUrl = url) }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onSuccess()
        }
    }
}
