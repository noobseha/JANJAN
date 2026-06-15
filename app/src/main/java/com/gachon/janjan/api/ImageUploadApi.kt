package com.gachon.janjan.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UploadResponse(
    val success: Boolean,
    val url: String?
)

interface ImageUploadApi {
    @Multipart
    @POST("/upload")
    fun uploadImage(
        @Part("userId") userId: RequestBody,
        @Part image: MultipartBody.Part
    ): Call<UploadResponse>
}
