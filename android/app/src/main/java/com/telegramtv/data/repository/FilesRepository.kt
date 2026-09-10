package com.telegramtv.data.repository

import com.telegramtv.data.api.TelePlayApi
import com.telegramtv.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for file operations.
 */
@Singleton
class FilesRepository @Inject constructor(
    private val api: TelePlayApi
) {

    /**
     * Get paginated list of files.
     */
    suspend fun getFiles(
        folderId: Int? = null,
        page: Int = 1,
        perPage: Int = 20,
        fileType: String? = null
    ): Result<PaginatedResponse<FileItem>> {
        return try {
            val response = api.getFiles(folderId, page, perPage, fileType)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch files: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single file by ID.
     */
    suspend fun getFile(fileId: Int): Result<FileItem> {
        return try {
            val response = api.getFile(fileId)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("File not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search files by name.
     */
    suspend fun searchFiles(query: String, limit: Int = 50): Result<List<FileItem>> {
        return try {
            val response = api.searchFiles(query, limit)
            if (response.isSuccessful) {
                Result.success(response.body()?.items ?: emptyList())
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaHome(limit: Int = 20): Result<MediaHomeResponse> {
        return try {
            val response = api.getMediaHome(limit)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to load media home: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavorites(): Result<List<FileItem>> {
        return try {
            val response = api.getFavorites()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load favorites"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFavorite(fileId: Int, favorite: Boolean): Result<Unit> {
        return try {
            val response = if (favorite) api.addFavorite(fileId) else api.removeFavorite(fileId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update favorite"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setWatched(fileId: Int, watched: Boolean): Result<FileItem> {
        return try {
            val response = api.setWatchedState(fileId, WatchedStateRequest(watched))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to update watched state"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaTags(kind: String? = null): Result<List<MediaTag>> {
        return try {
            val response = api.getMediaTags(kind)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load media tags"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun autoTagLibrary(limit: Int = 5000): Result<List<AutoTagResponse>> {
        return try {
            val response = api.autoTagLibrary(limit)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to auto-tag library"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename or move a file.
     */
    suspend fun updateFile(fileId: Int, name: String? = null, folderId: Int? = null): Result<FileItem> {
        return try {
            val update = FileUpdate(fileName = name, folderId = folderId)
            val response = api.updateFile(fileId, update)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a file.
     */
    suspend fun deleteFile(fileId: Int): Result<Unit> {
        return try {
            val response = api.deleteFile(fileId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get watch progress for a file.
     */
    suspend fun getWatchProgress(fileId: Int): Result<WatchProgress?> {
        return try {
            val response = api.getWatchProgress(fileId)
            if (response.isSuccessful) {
                Result.success(response.body())
            } else if (response.code() == 404) {
                Result.success(null)
            } else {
                Result.failure(Exception("Failed to get progress"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update watch progress for a file.
     */
    suspend fun updateWatchProgress(
        fileId: Int,
        position: Int,
        duration: Int?,
        completed: Boolean = false
    ): Result<WatchProgress> {
        return try {
            val update = WatchProgressUpdate(position, duration, completed)
            val response = api.updateWatchProgress(fileId, update)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update progress"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get TV browse data (continue watching, recent, folders).
     */
    suspend fun getTVBrowse(): Result<TVBrowseResponse> {
        return try {
            val response = api.getTVBrowse()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load home data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get continue watching list.
     */
    suspend fun getContinueWatching(): Result<List<FileItem>> {
        return try {
            val response = api.getContinueWatching()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load continue watching"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recently added files.
     */
    suspend fun getRecentFiles(limit: Int = 20): Result<List<FileItem>> {
        return try {
            val response = api.getRecentFilesWeb(limit)
            if (response.isSuccessful) {
                Result.success(response.body()?.items ?: emptyList())
            } else {
                // Fallback to tv endpoint if needed
                val fallback = api.getRecentFiles(limit)
                if (fallback.isSuccessful) Result.success(fallback.body() ?: emptyList())
                else Result.failure(Exception("Failed to load recent files"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get continue watching files from web parity endpoint.
     */
    suspend fun getContinueWatchingWeb(limit: Int = 20): Result<List<FileItem>> {
        return try {
            val response = api.getContinueWatchingWeb(limit)
            if (response.isSuccessful) {
                Result.success(response.body()?.items ?: emptyList())
            } else {
                val fallback = api.getContinueWatching()
                if (fallback.isSuccessful) Result.success(fallback.body() ?: emptyList())
                else Result.failure(Exception("Failed to load continue watching"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeContinueWatching(fileId: Int): Result<Unit> {
        return try {
            val response = api.removeContinueWatching(fileId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to remove continue watching item"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearContinueWatching(): Result<Unit> {
        return try {
            val response = api.clearContinueWatching()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to clear continue watching"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(query: String? = null): Result<List<HistoryEntry>> {
        return try {
            val response = api.getHistory(query)
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load history"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHistoryItem(id: Int): Result<Unit> {
        return try {
            val response = api.deleteHistoryItem(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete history item"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearHistory(): Result<Unit> {
        return try {
            val response = api.clearHistory()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to clear history"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCollections(): Result<List<MediaCollection>> {
        return try {
            val response = api.getCollections()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed to load collections"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCollection(name: String, description: String? = null): Result<MediaCollection> {
        return try {
            val response = api.createCollection(CreateCollectionRequest(name, description))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to create collection"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bulkAddToCollection(collectionId: Int, query: String): Result<MediaCollection> {
        return try {
            val response = api.bulkAddToCollection(collectionId, BulkAddCollectionRequest(query))
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to add to collection"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMediaStats(): Result<MediaStats> {
        return try {
            val response = api.getMediaStats()
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("Failed to load media statistics"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSurpriseMe(fileType: String? = null): Result<FileItem> {
        return try {
            val response = api.getSurpriseMe(fileType = fileType)
            if (response.isSuccessful) Result.success(response.body()!!)
            else Result.failure(Exception("No media found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchMedia(query: String, fileType: String? = null): Result<List<FileItem>> {
        return try {
            val response = api.searchMedia(query = query, fileType = fileType)
            if (response.isSuccessful) Result.success(response.body()?.files ?: emptyList())
            else Result.failure(Exception("Search failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Search for TV.
     */
    suspend fun searchTV(query: String): Result<SearchResponse> {
        return try {
            val response = api.searchTV(query)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(fileId: Int, isFavorite: Boolean): Result<Unit> {
        return setFavorite(fileId, isFavorite)
    }

    suspend fun updateWatchedState(fileId: Int, state: String): Result<Unit> {
        val watched = (state == "watched")
        return setWatched(fileId, watched).map { Unit }
    }

    /**
     * Build streaming URL for a file.
     */
    fun getStreamUrl(fileId: Int, serverUrl: String): String {
        return "${serverUrl}/api/stream/$fileId"
    }

    /**
     * Get a public streaming URL for a file.
     * This triggers the backend to generate a public hash if it doesn't exist.
     */
    suspend fun getPublicLink(fileId: Int, serverUrl: String): Result<String> {
        return try {
            val response = api.shareFile(fileId)
            if (response.isSuccessful) {
                val file = response.body()!!
                if (file.publicHash != null) {
                    Result.success("${serverUrl}/api/stream/s/${file.publicHash}")
                } else {
                    Result.failure(Exception("Failed to generate public hash"))
                }
            } else {
                Result.failure(Exception("Failed to share file: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revoke a public link for a file.
     */
    suspend fun revokeShare(fileId: Int): Result<FileItem> {
        return try {
            val response = api.revokeShare(fileId)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to revoke share"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Build thumbnail URL for a file.
     */
    fun getThumbnailUrl(fileId: Int, serverUrl: String): String {
        return "${serverUrl}/api/stream/$fileId/thumbnail"
    }
}
