package com.example.app1

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch

class UserOutfitsFragment : Fragment() {

    private lateinit var content: LinearLayout

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

        val title = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
            text = "Ваши образы"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp }
            text = "Сохраненные комплекты из вашего гардероба"
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 16f
        }

        content.addView(backButton)
        content.addView(title)
        content.addView(subtitle)
        scrollView.addView(content)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadOutfits()
    }

    private fun loadOutfits() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.getOutfits().outfits
            }.onSuccess { outfits ->
                renderOutfits(outfits)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось загрузить образы",
                    Toast.LENGTH_LONG
                ).show()
                renderOutfits(emptyList())
            }
        }
    }

    private fun renderOutfits(outfits: List<OutfitDto>) {
        while (content.childCount > HEADER_CHILDREN_COUNT) {
            content.removeViewAt(HEADER_CHILDREN_COUNT)
        }

        if (outfits.isEmpty()) {
            content.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 80.dp }
                gravity = Gravity.CENTER
                text = "Сохраненных образов пока нет"
                setTextColor(resources.getColor(R.color.gray, null))
                textSize = 18f
            })
            return
        }

        outfits.forEach { outfit ->
            content.addView(createOutfitCard(outfit))
        }
    }

    private fun createOutfitCard(outfit: OutfitDto): View {
        val card = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp }
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.bg_outfit_slot, null)
            setPadding(14.dp, 14.dp, 14.dp, 14.dp)
        }

        card.addView(TextView(requireContext()).apply {
            text = outfit.name?.takeIf { it.isNotBlank() } ?: "Образ"
            setTextColor(android.graphics.Color.BLACK)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
            orientation = LinearLayout.HORIZONTAL

            outfit.items.take(5).forEach { item ->
                addView(ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(64.dp, 64.dp).apply {
                        rightMargin = 8.dp
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = resources.getDrawable(R.drawable.bg_outfit_card, null)
                    setPadding(5.dp, 5.dp, 5.dp, 5.dp)
                    load(ApiUrl.resolveMediaUrl(item.normalized_image_url)) {
                        crossfade(true)
                        placeholder(R.drawable.ic_wardrobe)
                        error(R.drawable.ic_wardrobe)
                    }
                })
            }
        })

        card.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
            text = "Вещей: ${outfit.items.size}"
            setTextColor(resources.getColor(R.color.gray, null))
            textSize = 15f
        })

        return card
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val HEADER_CHILDREN_COUNT = 3
    }
}
