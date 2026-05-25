package com.androidtown.janjansup.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class KakaoPlace(
    val id: String,
    val place_name: String,
    val address_name: String,
    val road_address_name: String,
    val category_name: String,
    val phone: String,
    val place_url: String,
    val x: String,
    val y: String
)

data class KakaoSearchResponse(
    val documents: List<KakaoPlace>
)

interface KakaoApiService {
    @GET("v2/local/search/keyword.json")
    suspend fun searchPlaces(
        @Header("Authorization") apiKey: String,
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): KakaoSearchResponse
}