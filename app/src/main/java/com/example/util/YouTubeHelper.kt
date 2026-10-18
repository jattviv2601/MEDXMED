package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun isYouTubeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.trim().lowercase()
    return lower.contains("youtube.com") || lower.contains("youtu.be") || extractYouTubeVideoId(url) != null
}

fun extractYouTubeVideoId(url: String?): String? {
    return YouTubeHelper.extractVideoId(url.orEmpty())
}

data class YouTubeMetadata(
    val videoId: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String
)

object YouTubeHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun extractVideoId(url: String): String? {
        val clean = url.trim()
        if (clean.isBlank()) return null

        // Direct 11-char ID
        if (clean.length == 11 && !clean.contains("/") && !clean.contains("?") && !clean.contains(".")) {
            return clean
        }

        // Shorts URL: https://youtube.com/shorts/{id}?si=... or https://www.youtube.com/shorts/{id}
        if (clean.contains("/shorts/")) {
            val part = clean.substringAfter("/shorts/").substringBefore("?").substringBefore("&").substringBefore("/")
            if (part.isNotBlank()) return part.take(11)
        }

        // youtu.be/{id}?si=...
        if (clean.contains("youtu.be/")) {
            val part = clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("/")
            if (part.isNotBlank()) return part.take(11)
        }

        // watch?v={id}
        if (clean.contains("v=")) {
            val part = clean.substringAfter("v=").substringBefore("&").substringBefore("#").substringBefore("?")
            if (part.isNotBlank()) return part.take(11)
        }

        // embed/{id}
        if (clean.contains("/embed/")) {
            val part = clean.substringAfter("/embed/").substringBefore("?").substringBefore("&").substringBefore("/")
            if (part.isNotBlank()) return part.take(11)
        }

        return null
    }

    suspend fun fetchMetadata(urlOrId: String): YouTubeMetadata? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId) ?: return@withContext null
        val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"

        try {
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val title = json.optString("title", "High Yield Medical Short")
                        val author = json.optString("author_name", "MedXMed Faculty")
                        // Prefer maxresdefault with fallback to hqdefault
                        val highResThumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                        return@withContext YouTubeMetadata(
                            videoId = videoId,
                            title = title,
                            authorName = author,
                            thumbnailUrl = highResThumbnail
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback metadata if network or oEmbed fails
        YouTubeMetadata(
            videoId = videoId,
            title = "MBBS High Yield Short",
            authorName = "MedXMed Faculty",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
    }
}
