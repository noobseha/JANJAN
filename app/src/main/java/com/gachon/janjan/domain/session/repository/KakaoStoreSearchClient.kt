package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class KakaoStoreSearchClient(
    private val apiKey: String = BuildConfig.KAKAO_REST_API_KEY,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun search(keyword: String): List<KakaoPlace> = withContext(Dispatchers.IO) {
        val cleanedKeyword = keyword.trim()
        if (cleanedKeyword.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        check(apiKey.isNotBlank()) { "KAKAO_REST_API_KEY is not configured." }

        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", cleanedKeyword)
            .addQueryParameter("size", "15")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "KakaoAK $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Kakao store search failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val documents = JSONObject(body).optJSONArray("documents") ?: return@withContext emptyList()

            buildList {
                for (index in 0 until documents.length()) {
                    val document = documents.optJSONObject(index) ?: continue
                    add(
                        KakaoPlace(
                            id = document.optString("id"),
                            name = document.optString("place_name"),
                            address = document.optString("road_address_name")
                                .ifBlank { document.optString("address_name") },
                            roadAddress = document.optString("road_address_name"),
                            jibunAddress = document.optString("address_name"),
                            category = document.optString("category_name"),
                            phone = document.optString("phone"),
                            placeUrl = document.optString("place_url")
                        )
                    )
                }
            }
        }
    }

    private companion object {
        private const val SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val MIN_QUERY_LENGTH = 2
    }
}

data class KakaoPlace(
    val id: String,
    val name: String,
    val address: String,
    val roadAddress: String,
    val jibunAddress: String,
    val category: String,
    val phone: String,
    val placeUrl: String
)
