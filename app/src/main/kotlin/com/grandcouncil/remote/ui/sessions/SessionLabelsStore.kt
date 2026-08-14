package com.grandcouncil.remote.ui.sessions

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.labelsDataStore by preferencesDataStore(name = "session_labels")

/**
 * 会话本地标签（B3：收藏常用会话）。
 * key 格式 "profileId:sessionId"，本地 DataStore 存储（serve 无标签概念）。
 */
class SessionLabelsStore(private val context: Context) {

    private val favoritesKey = stringSetPreferencesKey("favorites")

    val favorites: Flow<Set<String>> = context.labelsDataStore.data.map { prefs ->
        prefs[favoritesKey] ?: emptySet()
    }

    suspend fun setFavorite(key: String, favorite: Boolean) {
        context.labelsDataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (favorite) current + key else current - key
        }
    }
}
