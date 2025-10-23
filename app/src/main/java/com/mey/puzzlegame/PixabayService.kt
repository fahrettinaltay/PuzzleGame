package com.mey.puzzlegame

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data class to match the structure of a single image object in the Pixabay API response
@Serializable
data class PixabayImage(
    val id: Int,
    @SerialName("webformatURL")
    val webformatURL: String, // Smaller, faster-loading image URL
    @SerialName("largeImageURL")
    val largeImageURL: String // Higher quality image URL for the puzzle
)

// Data class to match the top-level structure of the Pixabay API response
@Serializable
data class PixabayResponse(
    val total: Int,
    val totalHits: Int,
    val hits: List<PixabayImage>
)

// Service class to handle all interactions with the Pixabay API
class PixabayService {

    private val apiKey = "51425784-233c47305f8a24b856d17670b"

    // Configure the Ktor HttpClient
    private val client = HttpClient(Android) {
        // Install the ContentNegotiation plugin to handle JSON parsing
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // This is important as Pixabay might add new fields
            })
        }
        // Install the Logging plugin to see network requests in Logcat
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("KtorLogger", message)
                }
            }
        }
    }

    /**
     * Searches for images on Pixabay based on a query string.
     * @param query The search term (e.g., "cats", "nature").
     * @param lang The language code for the search (e.g., "en", "tr").
     * @param page The page number of the results to retrieve.
     * @return A PixabayResponse object containing images and total hits.
     */
    suspend fun searchImages(query: String, lang: String, page: Int): PixabayResponse? {
        if (apiKey == "BURAYA_YAPIŞTIR" || query.isBlank()) {
            return null
        }

        return try {
            client.get("https://pixabay.com/api/") {
                parameter("key", apiKey)
                parameter("q", query)
                parameter("lang", lang)
                parameter("image_type", "photo")
                parameter("safesearch", "true")
                parameter("per_page", 50) // We ask for 50 images per page
                parameter("page", page)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null in case of an error
        }
    }
}
