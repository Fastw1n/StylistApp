package com.example.app1

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class RecommendedOutfitsFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.beige, null))
            setPadding(20.dp, 20.dp, 20.dp, 24.dp)

            addView(ImageButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
                background = null
                contentDescription = "Назад"
                setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                setImageResource(R.drawable.ic_left_bttn)
                setOnClickListener { parentFragmentManager.popBackStack() }
            })

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12.dp }
                text = "Рекомендованные образы"
                setTextColor(resources.getColor(R.color.black, null))
                textSize = 30f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = Gravity.CENTER
                text = "Здесь появятся рекомендации"
                setTextColor(resources.getColor(R.color.gray, null))
                textSize = 18f
            })
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
