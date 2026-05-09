package com.example.app1

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ClothingItemsFragment : Fragment(R.layout.fragment_clothing_items) {

    private val adapter = WardrobeItemsAdapter(::openItemPreview)
    private var category: String? = null
    private var title: String = TITLE_ALL_ITEMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = arguments?.getString(ARG_CATEGORY)
        title = arguments?.getString(ARG_TITLE) ?: TITLE_ALL_ITEMS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.clothingItemsTitle).text = title
        view.findViewById<ImageButton>(R.id.clothingItemsBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
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
        val items = category
            ?.let { WardrobeContainer.getItems(requireContext(), it) }
            ?: WardrobeContainer.getAllItems(requireContext())

        adapter.submitItems(items)

        view.findViewById<TextView>(R.id.clothingItemsCount).text = "Вещей: ${items.size}"
        view.findViewById<TextView>(R.id.clothingItemsEmpty).isVisible = items.isEmpty()
        view.findViewById<RecyclerView>(R.id.clothingItemsRecycler).isVisible = items.isNotEmpty()
    }

    private fun openItemPreview(item: WardrobeItem) {
        val imageUrl = item.displayImageUrl()
        if (imageUrl == null) {
            Toast.makeText(requireContext(), "У этой вещи нет изображения", Toast.LENGTH_SHORT).show()
            return
        }

        WardrobeImagePreviewDialog.newInstance(imageUrl)
            .show(parentFragmentManager, "WardrobeImagePreviewDialog")
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
        private const val TITLE_ALL_ITEMS = "Все вещи"

        fun newInstance(category: String? = null, title: String = TITLE_ALL_ITEMS): ClothingItemsFragment =
            ClothingItemsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    category?.let { putString(ARG_CATEGORY, it) }
                }
            }
    }
}
