package com.example.app1

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
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

    @PUT("/v1/items/{item_id}/favorite")
    suspend fun updateItemFavorite(
        @Path("item_id") itemId: String,
        @Body request: FavoriteItemRequest
    ): ClothingItemDto

    @PUT("/v1/items/{item_id}")
    suspend fun updateItem(
        @Path("item_id") itemId: String,
        @Body request: UpdateItemRequest
    ): ClothingItemDto

    @DELETE("/v1/items/{item_id}")
    suspend fun deleteItem(
        @Path("item_id") itemId: String
    ): DeleteItemResponse

    @GET("/v1/outfits")
    suspend fun getOutfits(): OutfitsResponse

    @POST("/v1/outfits")
    suspend fun createOutfit(
        @Body request: CreateOutfitRequest
    ): OutfitDto

    @PUT("/v1/outfits/{outfit_id}")
    suspend fun updateOutfit(
        @Path("outfit_id") outfitId: String,
        @Body request: UpdateOutfitRequest
    ): OutfitDto

    @DELETE("/v1/outfits/{outfit_id}")
    suspend fun deleteOutfit(
        @Path("outfit_id") outfitId: String
    ): DeleteOutfitResponse
}
