package com.mey.puzzlegame

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Create the DataStore instance, accessible via context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private fun highScoreKey(size: Int) = intPreferencesKey("high_score_$size")
    private val themeKey = booleanPreferencesKey("dark_theme")

    // --- High Score ---

    fun getHighScore(size: Int): Flow<Int> {
        return context.dataStore.data.map {
            it[highScoreKey(size)] ?: 0
        }
    }

    suspend fun updateHighScore(size: Int, score: Int) {
        context.dataStore.edit { settings ->
            val key = highScoreKey(size)
            val currentHighScore = settings[key] ?: 0
            // Update if the new score is higher
            if (score > currentHighScore) {
                settings[key] = score
            }
        }
    }

    // --- Theme ---

    val isDarkTheme: Flow<Boolean>
        get() = context.dataStore.data.map { it[themeKey] ?: false }

    suspend fun toggleTheme() {
        context.dataStore.edit {
            it[themeKey] = !(it[themeKey] ?: false)
        }
    }
}