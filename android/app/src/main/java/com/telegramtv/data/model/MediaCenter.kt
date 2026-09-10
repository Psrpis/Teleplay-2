package com.telegramtv.data.model

import com.google.gson.annotations.SerializedName

data class MediaHomeResponse(
    @SerializedName("hero") val hero: FileItem?,
    @SerializedName("continue_watching") val continueWatching: List<FileItem> = emptyList(),
    @SerializedName("favorites") val favorites: List<FileItem> = emptyList(),
    @SerializedName("recently_added") val recentlyAdded: List<FileItem> = emptyList(),
    @SerializedName("recently_watched") val recentlyWatched: List<FileItem> = emptyList(),
    @SerializedName("collections") val collections: List<MediaCollection> = emptyList()
)

data class MediaCollection(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("item_count") val itemCount: Int = 0,
    @SerializedName("files") val files: List<FileItem> = emptyList()
)

data class FavoriteRequest(@SerializedName("file_id") val fileId: Int)
data class WatchedStateRequest(@SerializedName("watched") val watched: Boolean)
data class MediaSearchResponse(
    @SerializedName("files") val files: List<FileItem> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("per_page") val perPage: Int = 30
)

data class MediaTag(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("kind") val kind: String,
    @SerializedName("value") val value: String? = null,
    @SerializedName("file_count") val fileCount: Int = 0
)

data class AutoTagResponse(
    @SerializedName("file_id") val fileId: Int,
    @SerializedName("series") val series: String? = null,
    @SerializedName("actors") val actors: List<String> = emptyList(),
    @SerializedName("season") val season: Int? = null,
    @SerializedName("episode") val episode: Int? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList()
)

data class HistoryEntry(
    @SerializedName("id") val id: Int,
    @SerializedName("file_id") val fileId: Int,
    @SerializedName("watched_at") val watchedAt: String,
    @SerializedName("position") val position: Int? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("file") val file: FileItem? = null
)

data class MediaStats(
    @SerializedName("total_watched") val totalWatched: Int = 0,
    @SerializedName("movies_watched") val moviesWatched: Int = 0,
    @SerializedName("episodes_watched") val episodesWatched: Int = 0,
    @SerializedName("total_watch_time") val totalWatchTime: Long = 0L,
    @SerializedName("recent_activity") val recentActivity: List<FileItem> = emptyList()
)

data class CreateCollectionRequest(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null
)

data class BulkAddCollectionRequest(
    @SerializedName("query") val query: String
)

