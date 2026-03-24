package com.example.app1

import android.net.Uri
import java.util.UUID

data class WardrobeItem(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val imageUri: String
)

object WardrobeContainer {
    private val items = mutableListOf<WardrobeItem>()

    fun addItem(category: String, uri: Uri) {
        items.add(WardrobeItem(category = category, imageUri = uri.toString()))
    }

    fun getItems(category: String): List<WardrobeItem> =
        items.filter { it.category == category }.reversed()
}