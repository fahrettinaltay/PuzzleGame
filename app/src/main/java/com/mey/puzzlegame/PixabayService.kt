package com.mey.puzzlegame

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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

    // IMPORTANT: PASTE YOUR API KEY HERE
    // Get your key from: https://pixabay.com/api/docs/
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
    }

    /**
     * Searches for images on Pixabay based on a query string.
     * @param query The search term (e.g., "cats", "nature").
     * @return A list of PixabayImage objects.
     */
    suspend fun searchImages(query: String): List<PixabayImage> {
        // Avoid making a request if the API key is not set or the query is blank
        if (apiKey == "BURAYA_YAPIŞTIR" || query.isBlank()) {
            return emptyList()
        }

        return try {
            val response: PixabayResponse = client.get("https://pixabay.com/api/") {
                parameter("key", apiKey)
                parameter("q", query)
                parameter("image_type", "photo") // We only want photos
                parameter("safesearch", "true") // Filter out inappropriate content
            }.body()
            response.hits
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return an empty list in case of an error
        }
    }
}
