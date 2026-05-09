package com.example.app1

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import coil.load
import java.util.Locale

class DetectedItemsReviewDialogFragment : DialogFragment() {

    var items: List<DraftCandidateDto> = emptyList()
    var startIndex: Int = 0
    var onConfirmSelected: ((List<String>) -> Unit)? = null

    private var currentIndex = 0
    private val acceptedCandidateIds = linkedSetOf<String>()
    private val rejectedCandidateIds = linkedSetOf<String>()

    private lateinit var titleView: TextView
    private lateinit var counterView: TextView
    private lateinit var imageView: ImageView
    private lateinit var categoryView: TextView
    private lateinit var confidenceView: TextView
    private lateinit var detailsView: TextView
    private lateinit var selectedView: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_detected_items_review)
        dialog.setCanceledOnTouchOutside(false)

        currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        titleView = dialog.findViewById(R.id.detectedReviewTitle)
        counterView = dialog.findViewById(R.id.detectedReviewCounter)
        imageView = dialog.findViewById(R.id.detectedReviewImage)
        categoryView = dialog.findViewById(R.id.detectedReviewCategory)
        confidenceView = dialog.findViewById(R.id.detectedReviewConfidence)
        detailsView = dialog.findViewById(R.id.detectedReviewDetails)
        selectedView = dialog.findViewById(R.id.detectedReviewSelected)

        dialog.findViewById<Button>(R.id.rejectCurrentButton).setOnClickListener {
            currentItemOrNull()?.candidate_id?.let(rejectedCandidateIds::add)
            moveNextOrFinish()
        }

        dialog.findViewById<Button>(R.id.acceptCurrentButton).setOnClickListener {
            currentItemOrNull()?.candidate_id?.let { candidateId ->
                rejectedCandidateIds.remove(candidateId)
                acceptedCandidateIds.add(candidateId)
            }
            moveNextOrFinish()
        }

        dialog.findViewById<Button>(R.id.rejectAllButton).setOnClickListener {
            acceptedCandidateIds.clear()
            rejectedCandidateIds.clear()
            dismissAllowingStateLoss()
        }

        dialog.findViewById<Button>(R.id.acceptAllButton).setOnClickListener {
            val candidateIds = items
                .map { it.candidate_id }
                .filterNot(rejectedCandidateIds::contains)
            confirmAndDismiss(candidateIds)
        }

        updateUi()
        return dialog
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun updateUi() {
        val currentItem = currentItemOrNull() ?: return
        val category = currentItem.category?.takeIf { it.isNotBlank() } ?: "не определена"
        val subcategory = currentItem.subcategory?.takeIf { it.isNotBlank() } ?: "-"
        val confidence = currentItem.confidence

        titleView.text = "Найдено вещей: ${items.size}"
        counterView.text = "Вещь ${currentIndex + 1} из ${items.size}"
        categoryView.text = "Категория: $category"
        confidenceView.text = confidence?.let {
            "Точность ${String.format(Locale.US, "%.0f", it * 100)}%"
        } ?: "Точность -"
        detailsView.text = "Подкатегория: $subcategory"
        selectedView.text = "Выбрано: ${acceptedCandidateIds.size}   Отменено: ${rejectedCandidateIds.size}"

        imageView.load(ApiUrl.resolveMediaUrl(currentItem.normalized_image_url)) {
            crossfade(true)
            placeholder(R.drawable.ic_wardrobe)
            error(R.drawable.ic_wardrobe)
        }
    }

    private fun moveNextOrFinish() {
        if (currentIndex < items.lastIndex) {
            currentIndex += 1
            updateUi()
        } else {
            confirmAndDismiss(acceptedCandidateIds.toList())
        }
    }

    private fun confirmAndDismiss(candidateIds: List<String>) {
        if (candidateIds.isEmpty()) {
            Toast.makeText(requireContext(), "Ничего не сохранено", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        onConfirmSelected?.invoke(candidateIds)
        dismissAllowingStateLoss()
    }

    private fun currentItemOrNull(): DraftCandidateDto? {
        return items.getOrNull(currentIndex)
    }
}
