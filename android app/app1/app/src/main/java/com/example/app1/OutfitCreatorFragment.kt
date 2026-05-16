package com.example.app1

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class OutfitCreatorFragment : Fragment() {

    private lateinit var slotsContainer: LinearLayout
    private val selectedItems = linkedMapOf<OutfitSlot, WardrobeItem>()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            clipToPadding = false
            setPadding(20.dp, 20.dp, 20.dp, 24.dp)
        }

        val root = LinearLayout(requireContext()).apply {
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

        val title = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
            text = "Создать образ"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp }
            text = "Выберите вещи из гардероба по категориям"
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 16f
        }

        slotsContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 22.dp }
            orientation = LinearLayout.VERTICAL
        }

        val saveButton = Button(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                56.dp
            ).apply { topMargin = 18.dp }
            text = "Сохранить образ"
            isAllCaps = false
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)
            setOnClickListener { showSaveDialog() }
        }

        root.addView(backButton)
        root.addView(title)
        root.addView(subtitle)
        root.addView(slotsContainer)
        root.addView(saveButton)
        scrollView.addView(root)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderSlots()
        viewLifecycleOwner.lifecycleScope.launch {
            WardrobeSyncer.syncFromBackend(requireContext())
            renderSlots()
        }
    }

    private fun renderSlots() {
        slotsContainer.removeAllViews()
        OutfitSlot.entries.forEach { slot ->
            slotsContainer.addView(createSlotView(slot))
        }
    }

    private fun createSlotView(slot: OutfitSlot): View {
        val selectedItem = selectedItems[slot]
        val card = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                112.dp
            ).apply { bottomMargin = 12.dp }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.bg_outfit_slot, null)
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener { showItemPicker(slot) }
        }

        val imageBox = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(84.dp, 84.dp)
            background = resources.getDrawable(R.drawable.bg_outfit_card, null)
        }

        if (selectedItem == null) {
            imageBox.addView(TextView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                text = "+"
                setTextColor(android.graphics.Color.BLACK)
                textSize = 34f
            })
        } else {
            imageBox.addView(ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                load(selectedItem.displayImageUrl()) {
                    crossfade(true)
                    placeholder(R.drawable.ic_wardrobe)
                    error(R.drawable.ic_wardrobe)
                }
            })
        }

        val textGroup = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = 14.dp }
            orientation = LinearLayout.VERTICAL
        }

        textGroup.addView(TextView(requireContext()).apply {
            text = slot.title
            setTextColor(android.graphics.Color.BLACK)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        textGroup.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
            text = selectedItem?.displayName() ?: "Выбрать вещь"
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 15f
            maxLines = 2
        })

        card.addView(imageBox)
        card.addView(textGroup)
        return card
    }

    private fun showItemPicker(slot: OutfitSlot) {
        val items = WardrobeContainer.getAllItems(requireContext()).filter { slot.matches(it) }
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Нет вещей для категории: ${slot.title}", Toast.LENGTH_LONG).show()
            return
        }

        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = OutfitPickerAdapter(items) { item ->
                selectedItems[slot] = item
                renderSlots()
                pickerDialog?.dismiss()
            }
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }

        pickerDialog = AlertDialog.Builder(requireContext())
            .setTitle(slot.title)
            .setView(recyclerView)
            .setNegativeButton("Отмена", null)
            .create()

        pickerDialog?.show()
    }

    private var pickerDialog: AlertDialog? = null

    private fun showSaveDialog() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "Выберите хотя бы одну вещь", Toast.LENGTH_LONG).show()
            return
        }

        val container = FrameLayout(requireContext()).apply {
            val padding = 20.dp
            setPadding(padding, 12.dp, padding, 0)
        }
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = "Название образа"
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            setText("Мой образ")
            selectAll()
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Сохранить образ")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
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
                saveOutfit(name)
            }
        }

        dialog.show()
    }

    private fun saveOutfit(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.createOutfit(
                    CreateOutfitRequest(
                        name = name,
                        item_ids = selectedItems.values.map { it.id }
                    )
                )
            }.onSuccess {
                Toast.makeText(requireContext(), "Образ сохранен", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.framelayout, UserOutfitsFragment())
                    .addToBackStack("saved_user_outfits")
                    .commit()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось сохранить образ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

enum class OutfitSlot(val title: String) {
    TOP("Верх"),
    BOTTOM("Низ"),
    ACCESSORY("Аксессуар"),
    SHOES("Обувь"),
    HEADWEAR("Головной убор");

    fun matches(item: WardrobeItem): Boolean {
        val category = item.category.orEmpty().lowercase()
        val label = listOfNotNull(item.name, item.subcategory, item.category)
            .joinToString(" ")
            .lowercase()

        return when (this) {
            TOP -> category == "top" || category == "outerwear"
            BOTTOM -> category == "bottom"
            ACCESSORY -> category == "accessory"
            SHOES -> category == "shoes"
            HEADWEAR -> category == "accessory" && listOf(
                "hat",
                "cap",
                "head",
                "шап",
                "кеп",
                "панам",
                "голов"
            ).any { marker -> marker in label }
        }
    }
}

class OutfitPickerAdapter(
    private val items: List<WardrobeItem>,
    private val onItemClick: (WardrobeItem) -> Unit
) : RecyclerView.Adapter<OutfitPickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val root = LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                190.dp(parent)
            ).apply {
                setMargins(6.dp(parent))
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = parent.resources.getDrawable(R.drawable.bg_outfit_slot, null)
            setPadding(8.dp(parent), 8.dp(parent), 8.dp(parent), 8.dp(parent))
            isClickable = true
            isFocusable = true
        }

        val imageView = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                132.dp(parent)
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val title = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp(parent) }
            gravity = Gravity.CENTER
            maxLines = 2
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
        }

        root.addView(imageView)
        root.addView(title)
        return ViewHolder(root, imageView, title)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.displayName()
        holder.imageView.load(item.displayImageUrl()) {
            crossfade(true)
            placeholder(R.drawable.ic_wardrobe)
            error(R.drawable.ic_wardrobe)
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        val imageView: ImageView,
        val title: TextView
    ) : RecyclerView.ViewHolder(itemView)
}

private fun Int.dp(parent: ViewGroup): Int =
    (this * parent.resources.displayMetrics.density).toInt()
