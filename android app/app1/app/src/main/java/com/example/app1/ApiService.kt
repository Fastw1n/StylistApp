package com.example.app1

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT

interface ApiService {

    @POST("/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): AuthResponse

    @POST("/v1/auth/login")
    suspend fun login(
        @Body request: AuthRequest
    ): AuthResponse

    @GET("/v1/profile/body")
    suspend fun getBodyProfile(): BodyProfileDto

    @PUT("/v1/profile/body")
    suspend fun saveBodyProfile(
        @Body request: BodyProfileRequest
    ): BodyProfileDto

    @DELETE("/v1/profile/body")
    suspend fun resetBodyProfile(): BodyProfileDto

    @Multipart
    @POST("/v1/items/prepare")
    suspend fun prepareItem(
        @Part image: MultipartBody.Part
    ): PrepareResponse

    @POST("/v1/items/confirm")
    suspend fun confirmItem(
        @Body request: ConfirmRequest
    ): ConfirmResponse

    @GET("/v1/items")
    suspend fun getItems(): ItemsResponse
}
