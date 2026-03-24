package com.example.app1

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/v1/items/prepare")
    suspend fun prepareItem(
        @Part image: MultipartBody.Part
    ): PrepareResponse

    @POST("/v1/items/confirm")
    suspend fun confirmItem(
        @Body request: ConfirmRequest
    ): ItemResponse

    @GET("/v1/items")
    suspend fun getItems(): ItemsResponse
}