package com.example.app1

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import coil.load

class WardrobeImagePreviewDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val imageUrl = requireArguments().getString(ARG_IMAGE_URL)

        val root = FrameLayout(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.white, null))
        }

        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            contentDescription = "Фото вещи"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(16.dp, 72.dp, 16.dp, 24.dp)
            load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_wardrobe)
                error(R.drawable.ic_wardrobe)
            }
        }

        val closeButton = ImageButton(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(56.dp, 56.dp).apply {
                leftMargin = 12.dp
                topMargin = 16.dp
            }
            background = null
            contentDescription = "Закрыть"
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            setImageResource(R.drawable.ic_left_bttn)
            setOnClickListener {
                dismiss()
            }
        }

        root.addView(imageView)
        root.addView(closeButton)
        dialog.setContentView(root)

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(resources.getColor(R.color.white, null)))
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_IMAGE_URL = "image_url"

        fun newInstance(imageUrl: String): WardrobeImagePreviewDialog =
            WardrobeImagePreviewDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URL, imageUrl)
                }
            }
    }
}
