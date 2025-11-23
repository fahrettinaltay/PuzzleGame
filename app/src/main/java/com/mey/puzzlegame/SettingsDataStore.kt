package com.mey.puzzlegame

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val IS_DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")
        private val SHOW_TILE_NUMBERS_KEY = booleanPreferencesKey("show_tile_numbers")
        private val MOVE_SOUNDS_KEY = booleanPreferencesKey("move_sounds")
        private val CELEBRATION_SOUND_KEY = booleanPreferencesKey("celebration_sound")
        private val HIGH_SCORE_3_KEY = intPreferencesKey("high_score_3")
        private val HIGH_SCORE_4_KEY = intPreferencesKey("high_score_4")
        private val HIGH_SCORE_5_KEY = intPreferencesKey("high_score_5")
        private val SAVED_GAME_STATE_KEY = stringPreferencesKey("saved_game_state")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val UNLOCKED_ACHIEVEMENTS_KEY = stringSetPreferencesKey("unlocked_achievements")
    }

    val isDarkTheme: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[IS_DARK_THEME_KEY] ?: false }

    val showTileNumbers: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[SHOW_TILE_NUMBERS_KEY] ?: false }

    val moveSoundsEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[MOVE_SOUNDS_KEY] ?: true }

    val celebrationSoundEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[CELEBRATION_SOUND_KEY] ?: true }

    val language: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[LANGUAGE_KEY] ?: Locale.getDefault().language }

    val unlockedAchievements: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[UNLOCKED_ACHIEVEMENTS_KEY] ?: emptySet() }

    suspend fun unlockAchievement(achievementId: String) {
        dataStore.edit {
            val unlocked = it[UNLOCKED_ACHIEVEMENTS_KEY] ?: emptySet()
            it[UNLOCKED_ACHIEVEMENTS_KEY] = unlocked + achievementId
        }
    }

    suspend fun toggleTheme() {
        dataStore.edit { preferences ->
            preferences[IS_DARK_THEME_KEY] = !(preferences[IS_DARK_THEME_KEY] ?: false)
        }
    }

    suspend fun toggleShowTileNumbers() {
        dataStore.edit { preferences ->
            preferences[SHOW_TILE_NUMBERS_KEY] = !(preferences[SHOW_TILE_NUMBERS_KEY] ?: false)
        }
    }

    suspend fun toggleMoveSounds() {
        dataStore.edit { preferences ->
            preferences[MOVE_SOUNDS_KEY] = !(preferences[MOVE_SOUNDS_KEY] ?: true)
        }
    }

    suspend fun toggleCelebrationSound() {
        dataStore.edit { preferences ->
            preferences[CELEBRATION_SOUND_KEY] = !(preferences[CELEBRATION_SOUND_KEY] ?: true)
        }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    fun getHighScore(size: Int): Flow<Int> {
        val key = when (size) {
            3 -> HIGH_SCORE_3_KEY
            4 -> HIGH_SCORE_4_KEY
            5 -> HIGH_SCORE_5_KEY
            else -> throw IllegalArgumentException("Unsupported puzzle size: $size")
        }
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { preferences -> preferences[key] ?: 0 }
    }

    suspend fun updateHighScore(size: Int, score: Int) {
        val key = when (size) {
            3 -> HIGH_SCORE_3_KEY
            4 -> HIGH_SCORE_4_KEY
            5 -> HIGH_SCORE_5_KEY
            else -> throw IllegalArgumentException("Unsupported puzzle size: $size")
        }
        dataStore.edit { preferences ->
            preferences[key] = score
        }
    }

    val savedGameState: Flow<GameState?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences[SAVED_GAME_STATE_KEY]?.let {
                GameState.fromJson(it)
            }
        }

    suspend fun saveGameState(gameState: GameState) {
        dataStore.edit { preferences ->
            preferences[SAVED_GAME_STATE_KEY] = gameState.toJson()
        }
    }

    suspend fun clearSavedGame() {
        dataStore.edit { preferences ->
            preferences.remove(SAVED_GAME_STATE_KEY)
        }
    }
}
