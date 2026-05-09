package com.example.app1

data class AuthRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null
)

data class UserProfileDto(
    val user_id: String,
    val email: String,
    val name: String?
)

data class BodyProfileDto(
    val skin_tone: String? = null,
    val eye_color: String? = null,
    val hair_color: String? = null,
    val height_cm: Int? = null,
    val weight_kg: Int? = null,
    val chest_cm: Int? = null,
    val waist_cm: Int? = null
)

data class BodyProfileRequest(
    val skin_tone: String? = null,
    val eye_color: String? = null,
    val hair_color: String? = null,
    val height_cm: Int? = null,
    val weight_kg: Int? = null,
    val chest_cm: Int? = null,
    val waist_cm: Int? = null
)

data class AuthResponse(
    val token: String,
    val user: UserProfileDto
)

data class DraftCandidateDto(
    val candidate_id: String,
    val normalized_image_url: String,
    val category: String?,
    val subcategory: String?,
    val season: String?,
    val warmth_level: Int?,
    val confidence: Double?,
    val colors: List<ColorDto> = emptyList()
)

data class PrepareResponse(
    val draft_id: String,
    val original_image_url: String?,
    val items: List<DraftCandidateDto>,
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

data class ConfirmSelectedItem(
    val candidate_id: String,
    val user_overrides: Map<String, Any?>? = null
)

data class ConfirmRequest(
    val draft_id: String,
    val selected_items: List<ConfirmSelectedItem>
)

data class ConfirmedItemDto(
    val item_id: String,
    val normalized_image_url: String,
    val attributes: AttributesDto
)

data class ConfirmResponse(
    val items: List<ConfirmedItemDto>
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
