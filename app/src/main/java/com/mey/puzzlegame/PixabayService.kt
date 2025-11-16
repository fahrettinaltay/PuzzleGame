package com.mey.puzzlegame

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PixabayImage(
    val id: Int,
    @SerialName("webformatURL")
    val webformatURL: String,
    @SerialName("largeImageURL")
    val largeImageURL: String
)

@Serializable
data class PixabayResponse(
    val total: Int,
    val totalHits: Int,
    val hits: List<PixabayImage>
)

class PixabayService {

    // LÜTFEN KENDİ API ANAHTARINI BURAYA YAPIŞTIRDIĞINDAN EMİN OL
    private val apiKey = "51425784-233c47305f8a24b856d17670b"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun searchImages(query: String, lang: String, page: Int): PixabayResponse? {
        if (query.isBlank()) {
            return null
        }

        // Bu, şimdiye kadarki en güçlü hata yakalama bloğumuz.
        // Sadece 'Exception' değil, 'Error' dahil her şeyi yakalar.
        return try {
            client.get("https://pixabay.com/api/") {
                parameter("key", apiKey)
                parameter("q", query)
                parameter("lang", lang)
                parameter("image_type", "photo")
                parameter("safesearch", "true")
                parameter("per_page", 50)
                parameter("page", page)
            }.body()
        } catch (t: Throwable) { // <-- EN ÖNEMLİ DEĞİŞİKLİK BURASI
            // Bir çöküş yakalandı. Konsola yazdır ve null dönerek devam et.
            t.printStackTrace()
            null
        }
    }
}