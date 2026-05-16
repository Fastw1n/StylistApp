package com.example.app1

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ClothingItemsFragment : Fragment(R.layout.fragment_clothing_items) {

    private val adapter = WardrobeItemsAdapter(::openItemPreview, ::showItemMenu)
    private var category: String? = null
    private var title: String = TITLE_ALL_ITEMS
    private var favoriteOnly: Boolean = false
    private var searchMode: Boolean = false
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = arguments?.getString(ARG_CATEGORY)
        title = arguments?.getString(ARG_TITLE) ?: TITLE_ALL_ITEMS
        favoriteOnly = arguments?.getBoolean(ARG_FAVORITE_ONLY) ?: false
        searchMode = arguments?.getBoolean(ARG_SEARCH_MODE) ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.clothingItemsTitle).text = title
        view.findViewById<ImageButton>(R.id.clothingItemsBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val searchInputLayout = view.findViewById<TextInputLayout>(R.id.clothingSearchInputLayout)
        val searchEditText = view.findViewById<TextInputEditText>(R.id.clothingSearchEditText)
        searchInputLayout.isVisible = searchMode
        if (searchMode) {
            searchEditText.requestFocus()
            searchEditText.doAfterTextChanged { text ->
                searchQuery = text?.toString().orEmpty()
                renderItems(view)
            }
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.clothingItemsRecycler)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = adapter

        renderItems(view)
        syncItemsFromBackend()
    }

    override fun onResume() {
        super.onResume()
        view?.let(::renderItems)
    }

    private fun renderItems(view: View) {
        val sourceItems = when {
            favoriteOnly -> WardrobeContainer.getFavoriteItems(requireContext())
            category != null -> WardrobeContainer.getItems(requireContext(), category.orEmpty())
            else -> WardrobeContainer.getAllItems(requireContext())
        }

        val items = if (searchMode) {
            searchItemsByName(sourceItems, searchQuery)
        } else {
            sourceItems
        }

        adapter.submitItems(items)

        view.findViewById<TextView>(R.id.clothingItemsCount).text = if (searchMode) {
            "Найдено: ${items.size}"
        } else {
            "Вещей: ${items.size}"
        }
        view.findViewById<TextView>(R.id.clothingItemsEmpty).apply {
            text = when {
                searchMode && searchQuery.isBlank() -> "Введите название вещи"
                searchMode -> "По такому названию ничего не найдено"
                favoriteOnly -> "В избранном пока нет вещей"
                else -> "Здесь пока нет сохраненных вещей"
            }
            isVisible = items.isEmpty() || (searchMode && searchQuery.isBlank())
        }
        view.findViewById<RecyclerView>(R.id.clothingItemsRecycler).isVisible =
            items.isNotEmpty() && !(searchMode && searchQuery.isBlank())
    }

    private fun searchItemsByName(items: List<WardrobeItem>, query: String): List<WardrobeItem> {
        val normalizedQuery = query.normalizeForSearch()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        return items
            .mapNotNull { item ->
                val normalizedName = item.displayName().normalizeForSearch()
                val score = searchScore(normalizedName, normalizedQuery)
                if (score > 0) item to score else null
            }
            .sortedWith(
                compareByDescending<Pair<WardrobeItem, Int>> { it.second }
                    .thenBy { it.first.displayName().lowercase() }
            )
            .map { it.first }
    }

    private fun searchScore(name: String, query: String): Int {
        if (name.isBlank()) return 0
        if (name == query) return 100_000
        if (name.startsWith(query)) return 80_000 - (name.length - query.length).coerceAtLeast(0)

        val containsAt = name.indexOf(query)
        if (containsAt >= 0) {
            return 60_000 - containsAt * 100 - (name.length - query.length).coerceAtLeast(0)
        }

        val similarity = diceSimilarity(name, query)
        return if (similarity >= 0.45) {
            (similarity * 10_000).toInt()
        } else {
            0
        }
    }

    private fun diceSimilarity(left: String, right: String): Double {
        val leftBigrams = left.bigrams()
        val rightBigrams = right.bigrams()
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) return 0.0

        val rightCounts = rightBigrams.groupingBy { it }.eachCount().toMutableMap()
        var intersection = 0
        for (bigram in leftBigrams) {
            val count = rightCounts[bigram] ?: 0
            if (count > 0) {
                intersection += 1
                rightCounts[bigram] = count - 1
            }
        }

        return (2.0 * intersection) / (leftBigrams.size + rightBigrams.size)
    }

    private fun String.bigrams(): List<String> {
        val padded = " $this "
        if (padded.length < 2) return emptyList()
        return (0 until padded.length - 1).map { index ->
            padded.substring(index, index + 2)
        }
    }

    private fun String.normalizeForSearch(): String =
        lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-яa-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun openItemPreview(item: WardrobeItem) {
        val imageUrl = item.displayImageUrl()
        if (imageUrl == null) {
            Toast.makeText(requireContext(), "У этой вещи нет изображения", Toast.LENGTH_SHORT).show()
            return
        }

        WardrobeImagePreviewDialog.newInstance(imageUrl)
            .show(parentFragmentManager, "WardrobeImagePreviewDialog")
    }

    private fun showItemMenu(item: WardrobeItem, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(
                0,
                MENU_FAVORITE,
                0,
                if (item.isFavorite) "Убрать из избранного" else "Добавить в избранное"
            )
            menu.add(0, MENU_RENAME, 1, "Переименовать")
            menu.add(0, MENU_DELETE, 2, "Удалить")

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_FAVORITE -> {
                        if (item.isFavorite) {
                            setFavorite(item, isFavorite = false, name = item.name)
                        } else {
                            showFavoriteDialog(item)
                        }
                    }
                    MENU_RENAME -> showRenameDialog(item)
                    MENU_DELETE -> showDeleteDialog(item)
                }
                true
            }

            show()
        }
    }

    private fun showFavoriteDialog(item: WardrobeItem) {
        showNameDialog(
            title = "Добавить в избранное",
            message = "Назовите вещь так, как хотите видеть ее в избранном.",
            positiveText = "Добавить",
            initialName = item.name?.takeIf { it.isNotBlank() } ?: item.displayCategory(),
            onSubmit = { favoriteName ->
                setFavorite(item, isFavorite = true, name = favoriteName)
            }
        )
    }

    private fun showRenameDialog(item: WardrobeItem) {
        showNameDialog(
            title = "Переименовать вещь",
            message = "Имя меняется отдельно от категории и не влияет на данные распознавания.",
            positiveText = "Сохранить",
            initialName = item.name?.takeIf { it.isNotBlank() } ?: item.displayCategory(),
            onSubmit = { newName ->
                updateItemName(item, newName)
            }
        )
    }

    private fun showNameDialog(
        title: String,
        message: String,
        positiveText: String,
        initialName: String,
        onSubmit: (String) -> Unit
    ) {
        val container = FrameLayout(requireContext()).apply {
            val padding = 20.dp
            setPadding(padding, 12.dp, padding, 0)
        }
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = "Название"
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText(initialName)
            selectAll()
        }

        inputLayout.addView(editText)
        container.addView(inputLayout)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton(positiveText, null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = editText.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    inputLayout.error = "Введите название"
                    return@setOnClickListener
                }

                inputLayout.error = null
                dialog.dismiss()
                onSubmit(name)
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog(item: WardrobeItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить вещь?")
            .setMessage("Вещь будет удалена из гардероба и базы данных.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                deleteItem(item)
            }
            .show()
    }

    private fun setFavorite(item: WardrobeItem, isFavorite: Boolean, name: String?) {
        WardrobeContainer.updateFavorite(requireContext(), item.id, isFavorite, name)
        view?.let(::renderItems)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.updateItemFavorite(
                    itemId = item.id,
                    request = FavoriteItemRequest(
                        is_favorite = isFavorite,
                        name = name?.takeIf { it.isNotBlank() }
                    )
                )
            }.onSuccess {
                WardrobeSyncer.syncFromBackend(requireContext())
                view?.let(::renderItems)
                Toast.makeText(
                    requireContext(),
                    if (isFavorite) "Добавлено в избранное" else "Убрано из избранного",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                WardrobeContainer.updateFavorite(requireContext(), item.id, item.isFavorite, item.name)
                view?.let(::renderItems)
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось обновить избранное",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateItemName(item: WardrobeItem, name: String) {
        WardrobeContainer.updateName(requireContext(), item.id, name)
        view?.let(::renderItems)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.updateItem(
                    itemId = item.id,
                    request = UpdateItemRequest(name = name)
                )
            }.onSuccess {
                WardrobeSyncer.syncFromBackend(requireContext())
                view?.let(::renderItems)
                Toast.makeText(requireContext(), "Название сохранено", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                WardrobeContainer.updateName(requireContext(), item.id, item.name)
                view?.let(::renderItems)
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось переименовать вещь",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun deleteItem(item: WardrobeItem) {
        WardrobeContainer.deleteItem(requireContext(), item.id)
        view?.let(::renderItems)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.deleteItem(item.id)
            }.onSuccess {
                Toast.makeText(requireContext(), "Вещь удалена", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                WardrobeSyncer.syncFromBackend(requireContext())
                view?.let(::renderItems)
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось удалить вещь",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun syncItemsFromBackend() {
        viewLifecycleOwner.lifecycleScope.launch {
            WardrobeSyncer.syncFromBackend(requireContext())
                .onSuccess {
                view?.let(::renderItems)
            }
        }
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_TITLE = "title"
        private const val ARG_FAVORITE_ONLY = "favorite_only"
        private const val ARG_SEARCH_MODE = "search_mode"
        private const val MENU_FAVORITE = 1
        private const val MENU_RENAME = 2
        private const val MENU_DELETE = 3
        private const val TITLE_ALL_ITEMS = "Все вещи"

        fun newInstance(
            category: String? = null,
            title: String = TITLE_ALL_ITEMS,
            favoriteOnly: Boolean = false,
            searchMode: Boolean = false
        ): ClothingItemsFragment =
            ClothingItemsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_FAVORITE_ONLY, favoriteOnly)
                    putBoolean(ARG_SEARCH_MODE, searchMode)
                    category?.let { putString(ARG_CATEGORY, it) }
                }
            }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
