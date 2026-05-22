package com.example.app1

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class OutfitDetailFragment : Fragment() {

    private lateinit var content: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var itemsContainer: LinearLayout
    private lateinit var collageContainer: FrameLayout
    private var currentOutfit: OutfitDto? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.beige, null))
            clipToPadding = false
            setPadding(20.dp, 20.dp, 20.dp, 24.dp)
        }

        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val backButton = ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
            background = null
            contentDescription = "Назад"
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            setImageResource(R.drawable.ic_left_bttn)
            setOnClickListener { parentFragmentManager.popBackStack() }
        }

        val titleRow = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        titleText = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = "Образ"
            setTextColor(resources.getColor(R.color.black, null))
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        titleRow.addView(titleText)
        titleRow.addView(ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
            background = null
            contentDescription = "Действия с образом"
            setPadding(9.dp, 9.dp, 9.dp, 9.dp)
            setImageResource(R.drawable.ic_more_vert)
            setOnClickListener { anchor ->
                currentOutfit?.let { outfit -> showOutfitMenu(outfit, anchor) }
            }
        })

        collageContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                430.dp
            ).apply { topMargin = 18.dp }
            setBackgroundColor(resources.getColor(R.color.white, null))
        }

        itemsContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18.dp }
            orientation = LinearLayout.VERTICAL
        }

        content.addView(backButton)
        content.addView(titleRow)
        content.addView(collageContainer)
        content.addView(itemsContainer)
        scrollView.addView(content)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadOutfit()
    }

    private fun loadOutfit() {
        val outfitId = requireArguments().getString(ARG_OUTFIT_ID).orEmpty()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.getOutfits().outfits
                    .firstOrNull { outfit -> outfit.outfit_id == outfitId }
            }.onSuccess { outfit ->
                if (outfit == null) {
                    Toast.makeText(requireContext(), "Образ не найден", Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                } else {
                    renderOutfit(outfit)
                }
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось загрузить образ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderOutfit(outfit: OutfitDto) {
        currentOutfit = outfit
        titleText.text = outfit.displayName()
        renderCollage(outfit)
        renderItems(outfit)
    }

    private fun showOutfitMenu(outfit: OutfitDto, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, MENU_RENAME, 0, "Переименовать")
            menu.add(0, MENU_DELETE, 1, "Удалить")

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    MENU_RENAME -> showRenameDialog(outfit)
                    MENU_DELETE -> showDeleteDialog(outfit)
                }
                true
            }

            show()
        }
    }

    private fun showRenameDialog(outfit: OutfitDto) {
        showNameDialog(
            title = "Переименовать образ",
            initialName = outfit.displayName(),
            positiveText = "Сохранить",
            onSubmit = { newName -> updateOutfitName(outfit, newName) }
        )
    }

    private fun showNameDialog(
        title: String,
        initialName: String,
        positiveText: String,
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

    private fun updateOutfitName(outfit: OutfitDto, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.updateOutfit(
                    outfitId = outfit.outfit_id,
                    request = UpdateOutfitRequest(name = name)
                )
            }.onSuccess { updatedOutfit ->
                Toast.makeText(requireContext(), "Название сохранено", Toast.LENGTH_SHORT).show()
                renderOutfit(updatedOutfit)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось переименовать образ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showDeleteDialog(outfit: OutfitDto) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить образ?")
            .setMessage("Образ будет удален из ваших сохраненных образов.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                deleteOutfit(outfit)
            }
            .show()
    }

    private fun deleteOutfit(outfit: OutfitDto) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.deleteOutfit(outfit.outfit_id)
            }.onSuccess {
                Toast.makeText(requireContext(), "Образ удален", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось удалить образ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderCollage(outfit: OutfitDto) {
        collageContainer.removeAllViews()

        val width = resources.displayMetrics.widthPixels - 40.dp
        val height = 430.dp
        val sortedItems = outfit.items
            .sortedBy { item -> item.previewOrder() }
            .take(6)

        sortedItems.forEachIndexed { index, item ->
            val slot = outfitSlot(index, sortedItems.size)
            collageContainer.addView(ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (width * slot.widthRatio).toInt(),
                    (height * slot.heightRatio).toInt()
                ).apply {
                    leftMargin = (width * slot.leftRatio).toInt()
                    topMargin = (height * slot.topRatio).toInt()
                    gravity = Gravity.TOP or Gravity.START
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                load(ApiUrl.resolveMediaUrl(item.normalized_image_url)) {
                    crossfade(true)
                    placeholder(R.drawable.ic_wardrobe)
                    error(R.drawable.ic_wardrobe)
                }
            })
        }
    }

    private fun renderItems(outfit: OutfitDto) {
        itemsContainer.removeAllViews()

        itemsContainer.addView(TextView(requireContext()).apply {
            text = "Вещи в образе"
            setTextColor(resources.getColor(R.color.black, null))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        outfit.items.forEach { item ->
            itemsContainer.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
                text = item.name?.takeIf { it.isNotBlank() }
                    ?: item.subcategory?.takeIf { it.isNotBlank() }
                    ?: item.category
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 16f
            })
        }
    }

    private fun OutfitDto.displayName(): String =
        name?.takeIf { it.isNotBlank() } ?: "Образ"

    private fun OutfitItemDto.previewOrder(): Int =
        when (category.lowercase()) {
            "outerwear" -> 0
            "top" -> 1
            "bottom" -> 2
            "shoes" -> 3
            "accessory" -> 4
            else -> 5
        }

    private fun outfitSlot(index: Int, count: Int): OutfitSlotPosition {
        val slots = when (count) {
            1 -> listOf(
                OutfitSlotPosition(0.12f, 0.06f, 0.76f, 0.88f)
            )
            2 -> listOf(
                OutfitSlotPosition(0.04f, 0.12f, 0.45f, 0.76f),
                OutfitSlotPosition(0.51f, 0.12f, 0.45f, 0.76f)
            )
            3 -> listOf(
                OutfitSlotPosition(0.03f, 0.07f, 0.46f, 0.72f),
                OutfitSlotPosition(0.50f, 0.04f, 0.46f, 0.66f),
                OutfitSlotPosition(0.30f, 0.58f, 0.38f, 0.36f)
            )
            4 -> listOf(
                OutfitSlotPosition(0.03f, 0.04f, 0.45f, 0.52f),
                OutfitSlotPosition(0.52f, 0.04f, 0.45f, 0.52f),
                OutfitSlotPosition(0.08f, 0.56f, 0.36f, 0.38f),
                OutfitSlotPosition(0.56f, 0.56f, 0.36f, 0.38f)
            )
            else -> listOf(
                OutfitSlotPosition(0.02f, 0.03f, 0.42f, 0.50f),
                OutfitSlotPosition(0.50f, 0.02f, 0.48f, 0.60f),
                OutfitSlotPosition(0.06f, 0.54f, 0.34f, 0.34f),
                OutfitSlotPosition(0.42f, 0.62f, 0.24f, 0.25f),
                OutfitSlotPosition(0.66f, 0.62f, 0.28f, 0.28f),
                OutfitSlotPosition(0.26f, 0.78f, 0.20f, 0.17f)
            )
        }
        return slots[index.coerceAtMost(slots.lastIndex)]
    }

    private data class OutfitSlotPosition(
        val leftRatio: Float,
        val topRatio: Float,
        val widthRatio: Float,
        val heightRatio: Float
    )

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_OUTFIT_ID = "outfit_id"
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2

        fun newInstance(outfitId: String): OutfitDetailFragment =
            OutfitDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_OUTFIT_ID, outfitId)
                }
            }
    }
}
