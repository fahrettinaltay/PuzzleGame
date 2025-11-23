package com.mey.puzzlegame

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GameState(
    val size: Int,
    val moves: Int,
    val elapsedTime: Long,
    val imageUri: String?,
    val puzzle: List<Int>
) {
    fun toJson(): String {
        return Json.encodeToString(this)
    }

    companion object {
        fun fromJson(jsonString: String): GameState? {
            return try {
                Json.decodeFromString<GameState>(jsonString)
            } catch (e: Exception) {
                // Log the exception or handle it as needed
                null
            }
        }
    }
}
