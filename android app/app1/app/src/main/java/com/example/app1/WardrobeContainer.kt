package com.example.app1

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

data class WardrobeItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val imageUri: String? = null,
    val subcategory: String? = null,
    val season: String? = null,
    val warmthLevel: Int? = null,
    val colors: List<ColorDto>? = emptyList(),
    val isFavorite: Boolean = false
) {
    fun displayCategory(): String =
        category?.takeIf { it.isNotBlank() } ?: "Без категории"

    fun displayName(): String =
        name?.takeIf { it.isNotBlank() } ?: displayCategory()

    fun displayImageUrl(): String? =
        ApiUrl.resolveMediaUrl(imageUrl ?: imageUri)
}

object WardrobeContainer {
    private const val PREFS_NAME = "wardrobe_storage"
    private const val KEY_ITEMS = "items"

    private val gson = Gson()
    private val itemsType = object : TypeToken<List<WardrobeItem>>() {}.type
    private val colorsType = object : TypeToken<List<ColorDto>>() {}.type

    fun addConfirmedItems(context: Context, confirmedItems: List<ConfirmedItemDto>) {
        if (confirmedItems.isEmpty()) return

        val currentItems = getAllItems(context).toMutableList()
        val knownIds = currentItems.map { it.id }.toMutableSet()
        val newItems = confirmedItems
            .map { it.toWardrobeItem() }
            .filter { knownIds.add(it.id) }

        if (newItems.isEmpty()) return

        saveItems(context, newItems + currentItems)
    }

    fun syncRemoteItems(context: Context, remoteItems: List<ClothingItemDto>) {
        val currentItems = getAllItems(context)
        val currentItemsById = currentItems.associateBy { it.id }
        val syncedItems = remoteItems.map { item ->
            item.toWardrobeItem(currentItemsById[item.item_id])
        }

        saveItems(context, syncedItems)
    }

    fun addItem(context: Context, category: String, uri: Uri) {
        addItem(
            context = context,
            item = WardrobeItem(
                name = category,
                category = category,
                imageUrl = uri.toString(),
                imageUri = uri.toString()
            )
        )
    }

    fun addItem(context: Context, item: WardrobeItem) {
        val currentItems = getAllItems(context)
        saveItems(context, listOf(item) + currentItems.filterNot { it.id == item.id })
    }

    fun getAllItems(context: Context): List<WardrobeItem> {
        migrateLegacyPrefsIfNeeded(context)
        return runBlocking(Dispatchers.IO) {
            dao(context).getAll().map { it.toWardrobeItem() }
        }
    }

    fun getItems(context: Context, category: String): List<WardrobeItem> =
        getAllItems(context).filter { it.category.equals(category, ignoreCase = true) }

    fun getFavoriteItems(context: Context): List<WardrobeItem> =
        getAllItems(context).filter { it.isFavorite }

    fun updateFavorite(context: Context, itemId: String, isFavorite: Boolean, name: String?) {
        migrateLegacyPrefsIfNeeded(context)
        runBlocking(Dispatchers.IO) {
            dao(context).updateFavorite(
                id = itemId,
                isFavorite = isFavorite,
                name = name?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun updateName(context: Context, itemId: String, name: String?) {
        migrateLegacyPrefsIfNeeded(context)
        runBlocking(Dispatchers.IO) {
            dao(context).updateName(
                id = itemId,
                name = name?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun deleteItem(context: Context, itemId: String) {
        migrateLegacyPrefsIfNeeded(context)
        runBlocking(Dispatchers.IO) {
            dao(context).deleteById(itemId)
        }
    }

    fun clear(context: Context) {
        runBlocking(Dispatchers.IO) {
            dao(context).clear()
        }
        prefs(context).edit().remove(KEY_ITEMS).apply()
    }

    private fun saveItems(context: Context, items: List<WardrobeItem>) {
        migrateLegacyPrefsIfNeeded(context)
        runBlocking(Dispatchers.IO) {
            dao(context).replaceAll(items.mapIndexed { index, item -> item.toEntity(index) })
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun dao(context: Context): WardrobeItemDao =
        WardrobeCacheDatabase.getInstance(context).wardrobeItemDao()

    private fun migrateLegacyPrefsIfNeeded(context: Context) {
        val sharedPreferences = prefs(context)
        val json = sharedPreferences.getString(KEY_ITEMS, null) ?: return
        val legacyItems = runCatching {
            gson.fromJson<List<WardrobeItem>>(json, itemsType)
        }.getOrDefault(emptyList())

        runBlocking(Dispatchers.IO) {
            val itemDao = dao(context)
            if (legacyItems.isNotEmpty() && itemDao.count() == 0) {
                itemDao.replaceAll(legacyItems.mapIndexed { index, item -> item.toEntity(index) })
            }
        }

        sharedPreferences.edit().remove(KEY_ITEMS).apply()
    }

    private fun WardrobeItem.toEntity(sortOrder: Int): WardrobeItemEntity =
        WardrobeItemEntity(
            id = id,
            sortOrder = sortOrder,
            name = name,
            category = category,
            imageUrl = imageUrl,
            imageUri = imageUri,
            subcategory = subcategory,
            season = season,
            warmthLevel = warmthLevel,
            colorsJson = colors?.let { gson.toJson(it) },
            isFavorite = isFavorite
        )

    private fun WardrobeItemEntity.toWardrobeItem(): WardrobeItem =
        WardrobeItem(
            id = id,
            name = name,
            category = category,
            imageUrl = imageUrl,
            imageUri = imageUri,
            subcategory = subcategory,
            season = season,
            warmthLevel = warmthLevel,
            colors = colorsJson?.let(::parseColors).orEmpty(),
            isFavorite = isFavorite
        )

    private fun parseColors(json: String): List<ColorDto> =
        runCatching {
            gson.fromJson<List<ColorDto>>(json, colorsType)
        }.getOrDefault(emptyList())

    private fun ConfirmedItemDto.toWardrobeItem() =
        WardrobeItem(
            id = item_id,
            name = name,
            category = attributes.category ?: "Без категории",
            imageUrl = ApiUrl.resolveMediaUrl(normalized_image_url),
            subcategory = attributes.subcategory,
            season = attributes.season,
            warmthLevel = attributes.warmth_level,
            colors = attributes.colors,
            isFavorite = is_favorite
        )

    private fun ClothingItemDto.toWardrobeItem(existingItem: WardrobeItem?) =
        WardrobeItem(
            id = item_id,
            name = name ?: existingItem?.name,
            category = category,
            imageUrl = ApiUrl.resolveMediaUrl(normalized_image_url),
            imageUri = existingItem?.imageUri,
            subcategory = subcategory ?: existingItem?.subcategory,
            season = season ?: existingItem?.season,
            warmthLevel = warmth_level ?: existingItem?.warmthLevel,
            colors = existingItem?.colors.orEmpty(),
            isFavorite = is_favorite
        )
}
