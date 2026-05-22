package com.example.app1

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
    private lateinit var sidebarPanel: LinearLayout
    private lateinit var sidebarToggle: ImageButton
    private lateinit var shiftingContent: LinearLayout
    private val selectedItems = linkedMapOf<OutfitSlot, WardrobeItem>()
    private val selectedColors = linkedSetOf<OutfitPaletteColor>()
    private val draftSelectedColors = linkedSetOf<OutfitPaletteColor>()
    private var selectedStyle: OutfitStyle? = null
    private var selectedSeason: OutfitSeason? = null
    private var draftSelectedStyle: OutfitStyle? = null
    private var draftSelectedSeason: OutfitSeason? = null
    private var sidebarOpen = false
    private var styleExpanded = false
    private var seasonExpanded = false
    private var sidebarScrollY = 0

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val screenRoot = FrameLayout(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.beige, null))
        }

        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.beige, null))
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
            setTextColor(resources.getColor(R.color.black, null))
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
            setTextColor(resources.getColor(R.color.white, null))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.pastel_orange, null)
            )
            setOnClickListener { showSaveDialog() }
        }

        shiftingContent = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        root.addView(backButton)
        root.addView(title)
        root.addView(subtitle)
        shiftingContent.addView(slotsContainer)
        shiftingContent.addView(saveButton)
        root.addView(shiftingContent)
        scrollView.addView(root)

        sidebarPanel = LinearLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                SIDEBAR_WIDTH_DP.dp,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                fillColor = resources.getColor(R.color.white, null),
                strokeColor = resources.getColor(R.color.pastel_orange, null),
                strokeWidth = 1.dp,
                radius = 0f
            )
            elevation = 10f
        }

        sidebarToggle = ImageButton(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(44.dp, 54.dp, Gravity.START or Gravity.TOP)
                .apply { topMargin = 118.dp }
            background = roundedBackground(
                fillColor = resources.getColor(R.color.white, null),
                strokeColor = resources.getColor(R.color.pastel_orange, null),
                strokeWidth = 1.dp,
                radius = 8.dp.toFloat()
            )
            contentDescription = "Открыть параметры образа"
            setPadding(10.dp, 12.dp, 10.dp, 12.dp)
            setImageResource(R.drawable.ic_right_bttn)
            elevation = 12f
            setOnClickListener {
                sidebarOpen = !sidebarOpen
                applySidebarState(animate = true)
            }
        }

        renderSidebar()
        screenRoot.addView(scrollView)
        screenRoot.addView(sidebarPanel)
        screenRoot.addView(sidebarToggle)
        applySidebarState(animate = false)
        return screenRoot
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

    private fun renderSidebar(preserveScroll: Boolean = true) {
        val scrollYToRestore = if (preserveScroll) currentSidebarScrollY() else 0
        sidebarPanel.removeAllViews()

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipToPadding = false
        }

        val content = LinearLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 24.dp, 16.dp, 24.dp)
        }

        content.addView(TextView(requireContext()).apply {
            text = "Параметры"
            setTextColor(resources.getColor(R.color.black, null))
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        content.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
            text = "Настройте стиль, палитру и сезон"
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 13f
            maxLines = 2
        })

        content.addView(createExpandableSection(
            title = "Стиль",
            summary = draftSelectedStyle?.title ?: "Выберите стиль",
            expanded = styleExpanded,
            onToggle = {
                styleExpanded = !styleExpanded
                renderSidebar()
            },
            child = createStyleList()
        ))

        content.addView(createColorSection())

        content.addView(createExpandableSection(
            title = "Сезон",
            summary = draftSelectedSeason?.title ?: "Любой сезон",
            expanded = seasonExpanded,
            onToggle = {
                seasonExpanded = !seasonExpanded
                renderSidebar()
            },
            child = createSeasonList()
        ))

        content.addView(createSidebarActions())

        scrollView.addView(content)
        sidebarPanel.addView(scrollView)
        sidebarScrollY = scrollYToRestore
        scrollView.post {
            scrollView.scrollTo(0, scrollYToRestore)
        }
    }

    private fun currentSidebarScrollY(): Int =
        (sidebarPanel.getChildAt(0) as? ScrollView)?.scrollY ?: sidebarScrollY

    private fun createExpandableSection(
        title: String,
        summary: String,
        expanded: Boolean,
        onToggle: () -> Unit,
        child: View
    ): View {
        val section = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18.dp }
            orientation = LinearLayout.VERTICAL
        }

        section.addView(LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(0, 8.dp, 0, 8.dp)
            setOnClickListener { onToggle() }

            addView(LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL

                addView(TextView(requireContext()).apply {
                    text = title
                    setTextColor(resources.getColor(R.color.black, null))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dp }
                    text = summary
                    setTextColor(resources.getColor(R.color.gray, null))
                    textSize = 13f
                    maxLines = 2
                })
            })

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp)
                gravity = Gravity.CENTER
                text = if (expanded) "v" else ">"
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 18f
            })
        })

        if (expanded) {
            section.addView(child)
        }

        return section
    }

    private fun createStyleList(): View =
        LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            OutfitStyle.entries.forEach { style ->
                addView(createChoiceRow(
                    title = style.title,
                    selected = draftSelectedStyle == style,
                    onClick = {
                        draftSelectedStyle = style
                        renderSidebar()
                    }
                ))
            }
        }

    private fun createSeasonList(): View =
        LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            OutfitSeason.entries.forEach { season ->
                addView(createChoiceRow(
                    title = season.title,
                    selected = draftSelectedSeason == season,
                    onClick = {
                        draftSelectedSeason = season
                        renderSidebar()
                    }
                ))
            }
        }

    private fun createChoiceRow(title: String, selected: Boolean, onClick: () -> Unit): View =
        LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(
                fillColor = if (selected) {
                    resources.getColor(R.color.pastel_orange_scrim, null)
                } else {
                    resources.getColor(R.color.beige, null)
                },
                strokeColor = if (selected) {
                    resources.getColor(R.color.pastel_orange, null)
                } else {
                    Color.TRANSPARENT
                },
                strokeWidth = 1.dp,
                radius = 8.dp.toFloat()
            )
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = title
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 14f
            })

            addView(TextView(requireContext()).apply {
                text = if (selected) "✓" else ""
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun createColorSection(): View {
        val section = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 18.dp }
            orientation = LinearLayout.VERTICAL
        }

        section.addView(TextView(requireContext()).apply {
            text = "Цвета"
            setTextColor(resources.getColor(R.color.black, null))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        section.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dp }
            text = if (draftSelectedColors.isEmpty()) {
                "Можно выбрать несколько"
            } else {
                draftSelectedColors.joinToString(", ") { color -> color.title }
            }
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 13f
            maxLines = 3
        })

        OutfitPaletteColor.entries.chunked(2).forEach { rowColors ->
            section.addView(LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp }
                orientation = LinearLayout.HORIZONTAL

                rowColors.forEach { color ->
                    addView(createColorChip(color))
                }

                if (rowColors.size == 1) {
                    addView(View(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    })
                }
            })
        }

        return section
    }

    private fun createColorChip(color: OutfitPaletteColor): View {
        val selected = color in draftSelectedColors
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 42.dp, 1f).apply {
                rightMargin = 6.dp
            }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(
                fillColor = if (selected) {
                    resources.getColor(R.color.pastel_orange_scrim, null)
                } else {
                    resources.getColor(R.color.beige, null)
                },
                strokeColor = if (selected) {
                    resources.getColor(R.color.pastel_orange, null)
                } else {
                    Color.TRANSPARENT
                },
                strokeWidth = 1.dp,
                radius = 8.dp.toFloat()
            )
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (selected) {
                    draftSelectedColors.remove(color)
                } else {
                    draftSelectedColors.add(color)
                }
                renderSidebar()
            }

            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(16.dp, 16.dp).apply {
                    rightMargin = 6.dp
                }
                background = roundedBackground(
                    fillColor = Color.parseColor(color.hex),
                    strokeColor = resources.getColor(R.color.gray, null),
                    strokeWidth = 1.dp,
                    radius = 8.dp.toFloat()
                )
            })

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = color.title
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 12f
                maxLines = 1
            })

            addView(TextView(requireContext()).apply {
                text = if (selected) "✓" else ""
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun createSidebarActions(): View =
        LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 22.dp }
            orientation = LinearLayout.VERTICAL

            addView(Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                )
                text = "Применить"
                isAllCaps = false
                textSize = 15f
                setTextColor(resources.getColor(R.color.white, null))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.pastel_orange, null)
                )
                setOnClickListener { applySidebarSelections() }
            })

            addView(Button(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dp
                ).apply { topMargin = 10.dp }
                text = "Сбросить"
                isAllCaps = false
                textSize = 15f
                setTextColor(resources.getColor(R.color.black, null))
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.beige, null)
                )
                setOnClickListener { resetSidebarSelections() }
            })
        }

    private fun applySidebarSelections() {
        selectedStyle = draftSelectedStyle
        selectedSeason = draftSelectedSeason
        selectedColors.clear()
        selectedColors.addAll(draftSelectedColors)
        renderSidebar()
        Toast.makeText(requireContext(), "Параметры применены", Toast.LENGTH_SHORT).show()
    }

    private fun resetSidebarSelections() {
        draftSelectedStyle = null
        draftSelectedSeason = null
        draftSelectedColors.clear()
        selectedStyle = null
        selectedSeason = null
        selectedColors.clear()
        renderSidebar()
        Toast.makeText(requireContext(), "Параметры сброшены", Toast.LENGTH_SHORT).show()
    }

    private fun applySidebarState(animate: Boolean) {
        val sidebarWidth = SIDEBAR_WIDTH_DP.dp.toFloat()
        val contentShift = CONTENT_SHIFT_DP.dp.toFloat()
        val panelTranslation = if (sidebarOpen) 0f else -sidebarWidth
        val contentTranslation = if (sidebarOpen) contentShift else 0f
        val toggleTranslation = if (sidebarOpen) sidebarWidth else 0f

        sidebarToggle.contentDescription = if (sidebarOpen) {
            "Скрыть параметры образа"
        } else {
            "Открыть параметры образа"
        }
        sidebarToggle.setImageResource(
            if (sidebarOpen) R.drawable.ic_left_bttn else R.drawable.ic_right_bttn
        )

        if (animate) {
            sidebarPanel.animate().translationX(panelTranslation).setDuration(220).start()
            shiftingContent.animate().translationX(contentTranslation).setDuration(220).start()
            sidebarToggle.animate().translationX(toggleTranslation).setDuration(220).start()
        } else {
            sidebarPanel.translationX = panelTranslation
            shiftingContent.translationX = contentTranslation
            sidebarToggle.translationX = toggleTranslation
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
                setTextColor(resources.getColor(R.color.black, null))
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
            setTextColor(resources.getColor(R.color.black, null))
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
                        item_ids = selectedItems.values.map { it.id },
                        style = selectedStyle?.apiValue,
                        colors = selectedColors.map { color -> color.apiValue },
                        season = selectedSeason?.apiValue
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

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0,
        radius: Float = 8.dp.toFloat()
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }

    companion object {
        private const val SIDEBAR_WIDTH_DP = 250
        private const val CONTENT_SHIFT_DP = 94
    }
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

private enum class OutfitStyle(val title: String, val apiValue: String) {
    CASUAL("Повседневный", "casual"),
    CLASSIC("Классический", "classic"),
    BUSINESS("Деловой", "business"),
    SPORT("Спортивный", "sport"),
    ROMANTIC("Романтичный", "romantic"),
    MINIMAL("Минимализм", "minimal"),
    EVENING("Вечерний", "evening")
}

private enum class OutfitSeason(val title: String, val apiValue: String) {
    ALL("Любой сезон", "all_season"),
    WINTER("Зима", "winter"),
    SPRING("Весна", "spring"),
    SUMMER("Лето", "summer"),
    AUTUMN("Осень", "autumn")
}

private enum class OutfitPaletteColor(
    val title: String,
    val apiValue: String,
    val hex: String
) {
    BLACK("Черный", "black", "#1F1F1F"),
    WHITE("Белый", "white", "#FFFFFF"),
    GRAY("Серый", "gray", "#9C9C9C"),
    BEIGE("Бежевый", "beige", "#D8C3A5"),
    BROWN("Коричневый", "brown", "#7A4E2D"),
    BLUE("Синий", "blue", "#2F5EA8"),
    GREEN("Зеленый", "green", "#4F7D55"),
    RED("Красный", "red", "#B53D3D"),
    PINK("Розовый", "pink", "#D990A6"),
    PURPLE("Фиолетовый", "purple", "#7A5C99"),
    YELLOW("Желтый", "yellow", "#E2C44F"),
    ORANGE("Оранжевый", "orange", "#D48668")
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
            setTextColor(parent.resources.getColor(R.color.black, null))
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
