package com.example.app1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class WardrobeItemsAdapter(
    private val onItemClick: (WardrobeItem) -> Unit = {}
) : RecyclerView.Adapter<WardrobeItemsAdapter.WardrobeItemViewHolder>() {

    private val items = mutableListOf<WardrobeItem>()

    fun submitItems(newItems: List<WardrobeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WardrobeItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wardrobe_item, parent, false)
        return WardrobeItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: WardrobeItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class WardrobeItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.itemImage)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        private val detailsText: TextView = itemView.findViewById(R.id.detailsText)

        fun bind(item: WardrobeItem) {
            imageView.load(item.displayImageUrl()) {
                crossfade(true)
                placeholder(R.drawable.ic_wardrobe)
                error(R.drawable.ic_wardrobe)
            }

            categoryText.text = item.displayCategory()
            detailsText.text = item.detailsText()
        }

        private fun WardrobeItem.detailsText(): String {
            val parts = mutableListOf<String>()

            if (!subcategory.isNullOrBlank()) {
                parts.add(subcategory)
            }

            if (!season.isNullOrBlank()) {
                parts.add("Сезон: $season")
            }

            warmthLevel?.let {
                parts.add("Теплота: $it")
            }

            val colorNames = colors.orEmpty()
                .mapNotNull { color -> color.name.takeIf { it.isNotBlank() } }
                .take(3)

            if (colorNames.isNotEmpty()) {
                parts.add("Цвета: ${colorNames.joinToString(", ")}")
            }

            return parts.joinToString("\n").ifBlank { "Данные распознавания не указаны" }
        }
    }
}
