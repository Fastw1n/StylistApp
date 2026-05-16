package com.example.app1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApiModelsTest {
    @Test
    fun confirmSelectedItem_defaultsOverridesToNull() {
        val selected = ConfirmSelectedItem(candidate_id = "candidate-1")

        assertEquals("candidate-1", selected.candidate_id)
        assertNull(selected.user_overrides)
    }

    @Test
    fun clothingItemDto_defaultsFavoriteToFalse() {
        val item = ClothingItemDto(
            item_id = "item-1",
            category = "top",
            subcategory = "shirt",
            normalized_image_url = "/media/normalized/shirt.png",
            season = "summer",
            warmth_level = 1
        )

        assertFalse(item.is_favorite)
        assertNull(item.name)
    }

    @Test
    fun createOutfitRequest_keepsNameAndItemIds() {
        val request = CreateOutfitRequest(
            name = "Weekend",
            item_ids = listOf("top-1", "shoes-1")
        )

        assertEquals("Weekend", request.name)
        assertEquals(listOf("top-1", "shoes-1"), request.item_ids)
    }

    @Test
    fun wardrobeItem_displayNameFallsBackToCategory() {
        val item = WardrobeItem(
            id = "item-1",
            name = " ",
            category = "top"
        )

        assertEquals("top", item.displayName())
    }
}
