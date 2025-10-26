package com.mey.puzzlegame

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Create the DataStore instance, accessible via context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class GameState(
    val size: Int,
    val moves: Int,
    val elapsedTime: Long, // Store elapsed time instead of start time
    val imageUri: String?,
    val puzzle: List<Int> // Flattened list of puzzle values
)

class SettingsDataStore(private val context: Context) {

    companion object {
        private fun highScoreKey(size: Int) = intPreferencesKey("high_score_$size")
        private val THEME_KEY = booleanPreferencesKey("dark_theme")
        private val SAVED_GAME_STATE = stringPreferencesKey("saved_game_state")
    }

    // --- Game State Persistence ---

    val savedGameState: Flow<GameState?> = context.dataStore.data.map {
        it[SAVED_GAME_STATE]?.let { jsonString ->
            try {
                Json.decodeFromString<GameState>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                null // In case of a deserialization error, return null
            }
        }
    }

    suspend fun saveGameState(gameState: GameState?) {
        context.dataStore.edit {
            if (gameState != null) {
                it[SAVED_GAME_STATE] = Json.encodeToString(gameState)
            } else {
                it.remove(SAVED_GAME_STATE)
            }
        }
    }

    suspend fun clearSavedGame() {
        context.dataStore.edit {
            it.remove(SAVED_GAME_STATE)
        }
    }

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
            if (score > currentHighScore) {
                settings[key] = score
            }
        }
    }

    // --- Theme ---

    val isDarkTheme: Flow<Boolean>
        get() = context.dataStore.data.map { it[THEME_KEY] ?: false }

    suspend fun toggleTheme() {
        context.dataStore.edit {
            it[THEME_KEY] = !(it[THEME_KEY] ?: false)
        }
    }
}