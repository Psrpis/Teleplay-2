package com.telegramtv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telegramtv.data.model.MediaTag
import com.telegramtv.data.model.SeriesSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mediaCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "media_cache")

/**
 * Local cache for the Series/Actors summary lists. Lets those screens show
 * something instantly on open instead of a blank spinner while the network
 * call is still in flight, and lets the caller diff against the fresh
 * result to decide whether a re-render is actually needed.
 */
@Singleton
class MediaCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val seriesKey = stringPreferencesKey("series_summary_cache")
    private val actorsKey = stringPreferencesKey("actors_cache")

    suspend fun getCachedSeries(): List<SeriesSummary> {
        val json = context.mediaCacheDataStore.data.first()[seriesKey] ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<SeriesSummary>>() {}.type
            gson.fromJson<List<SeriesSummary>>(json, type)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSeries(list: List<SeriesSummary>) {
        context.mediaCacheDataStore.edit { it[seriesKey] = gson.toJson(list) }
    }

    suspend fun getCachedActors(): List<MediaTag> {
        val json = context.mediaCacheDataStore.data.first()[actorsKey] ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<MediaTag>>() {}.type
            gson.fromJson<List<MediaTag>>(json, type)
        }.getOrDefault(emptyList())
    }

    suspend fun saveActors(list: List<MediaTag>) {
        context.mediaCacheDataStore.edit { it[actorsKey] = gson.toJson(list) }
    }
}
