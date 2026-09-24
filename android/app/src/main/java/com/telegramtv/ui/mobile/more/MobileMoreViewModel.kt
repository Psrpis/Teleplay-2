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
    val posterUrl: String? = null,
    val tagValue: String? = null
)

data class MoreUiState(
    val currentSubScreen: MoreSubScreen = MoreSubScreen.MENU,
    val isLoading: Boolean = false,
    val serverUrl: String = "",
    val userName: String? = null,

    // Movies
    val movies: List<FileItem> = emptyList(),
    val movieFilter: String = "ALL", // ALL, UNWATCHED, WATCHED
    val movieSort: String = "RECENT", // RECENT, TITLE, LENGTH

    // Series
    val seriesList: List<SeriesInfo> = emptyList(),
    val selectedSeries: SeriesInfo? = null,
    val isLoadingDetail: Boolean = false,
    val selectedSeason: Int = 1,
    val seriesSort: String = "DATE_DESC", // DATE_DESC, TITLE, SIZE_DESC

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
    private val authRepository: AuthRepository,
    private val mediaCacheStore: com.telegramtv.data.repository.MediaCacheStore
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
            val allVideos = loadAllVideoFiles()
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

    fun setMovieSort(sort: String) {
        _uiState.update { it.copy(movieSort = sort) }
    }

    fun setSeriesSort(sort: String) {
        _uiState.update { it.copy(seriesSort = sort) }
    }

    fun loadSeries() {
        viewModelScope.launch {
            // Show whatever we cached from last time immediately, if any.
            val cached = mediaCacheStore.getCachedSeries()
            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(seriesList = cached.toSeriesInfoList(), isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = true) }
            }

            // One cheap, constant-cost query regardless of library size.
            val fresh = filesRepository.getSeriesSummary().getOrNull()
            if (fresh == null) {
                if (cached.isEmpty()) _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val changed = fresh.size != cached.size || fresh.any { f ->
                cached.none { it.tagValue == f.tagValue && it.episodeCount == f.episodeCount }
            }
            if (changed) {
                _uiState.update { it.copy(seriesList = fresh.toSeriesInfoList(), isLoading = false) }
                mediaCacheStore.saveSeries(fresh)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun List<com.telegramtv.data.model.SeriesSummary>.toSeriesInfoList(): List<SeriesInfo> =
        map { summary ->
            SeriesInfo(
                name = summary.name,
                totalEpisodes = summary.episodeCount,
                seasons = emptyMap(),
                posterUrl = summary.posterUrl,
                tagValue = summary.tagValue
            )
        }.sortedBy { it.name }

    private suspend fun loadAllVideoFiles(): List<FileItem> {
        val videos = mutableListOf<FileItem>()
        var page = 1
        val pageSize = 100
        while (page <= 100) {
            val result = filesRepository.getFiles(
                folderId = null,
                page = page,
                perPage = pageSize,
                fileType = "video"
            )
            val response = result.getOrNull() ?: break
            videos += response.items
            if (response.items.isEmpty() || videos.size >= response.total || response.items.size < pageSize) break
            page++
        }
        return videos.distinctBy { it.id }
    }

    private fun cleanSeriesName(value: String): String = value
        .replace(Regex("[._]?[Ss]\\d{1,2}[Ee]\\d{1,2}.*$"), "")
        .replace(Regex("[._ -]+$"), "")
        .trim()

    fun selectSeries(series: SeriesInfo) {
        // Episodes weren't fetched for the grid (just name/count/poster), so
        // pull them now — only for the one series the user actually opened.
        _uiState.update { it.copy(selectedSeries = series, isLoadingDetail = true) }
        viewModelScope.launch {
            val episodes = filesRepository.searchMedia(
                query = "",
                tag = series.tagValue ?: series.name
            ).getOrNull().orEmpty()
            val seasons = episodes.groupBy { it.metadata?.season ?: 1 }
            val initialSeason = seasons.keys.minOrNull() ?: 1
            _uiState.update {
                it.copy(
                    selectedSeries = series.copy(seasons = seasons, totalEpisodes = episodes.size),
                    selectedSeason = initialSeason,
                    isLoadingDetail = false
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season) }
    }

    fun loadActors() {
        viewModelScope.launch {
            val cached = mediaCacheStore.getCachedActors()
            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(actors = cached, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = true) }
            }
            val fresh = filesRepository.getMediaTags(kind = "actor").getOrNull() ?: run {
                if (cached.isEmpty()) _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val changed = fresh.size != cached.size || fresh.any { f ->
                cached.none { it.name == f.name && it.fileCount == f.fileCount }
            }
            if (changed) {
                _uiState.update { it.copy(actors = fresh, isLoading = false) }
                mediaCacheStore.saveActors(fresh)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectActor(actor: MediaTag) {
        _uiState.update { it.copy(selectedActor = actor, isLoading = true) }
        viewModelScope.launch {
            val filtered = filesRepository.searchMedia(
                query = "",
                tag = actor.value ?: actor.name
            ).getOrNull().orEmpty()
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

    fun updateWatchedState(fileId: Int, state: String) {
        viewModelScope.launch {
            filesRepository.updateWatchedState(fileId, state)
            if (_uiState.value.selectedDetailFile?.id == fileId) {
                _uiState.update { it.copy(selectedDetailFile = it.selectedDetailFile?.copy(watchedState = state)) }
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
