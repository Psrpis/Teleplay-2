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
