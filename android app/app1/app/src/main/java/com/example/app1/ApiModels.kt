package com.example.app1

data class PrepareResponse(
    val draft_id: String,
    val normalized_image_url: String,
    val original_image_url: String?,
    val attributes: AttributesDto,
    val needs_user_review: Boolean
)

data class AttributesDto(
    val category: String?,
    val subcategory: String?,
    val season: String?,
    val warmth_level: Int?,
    val confidence: Double?,
    val colors: List<ColorDto> = emptyList()
)

data class ColorDto(
    val name: String,
    val hex: String?,
    val share: Double?
)

data class ConfirmRequest(
    val draft_id: String,
    val user_overrides: Map<String, Any?>? = null,
    val tags: List<String>? = null
)

data class ItemResponse(
    val item_id: String,
    val normalized_image_url: String,
    val attributes: AttributesDto
)

data class ClothingItemDto(
    val item_id: String,
    val category: String,
    val subcategory: String?,
    val normalized_image_url: String,
    val season: String?,
    val warmth_level: Int?
)

data class ItemsResponse(
    val items: List<ClothingItemDto>
)