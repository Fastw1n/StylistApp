package com.example.app1

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class CameraPageFragment : Fragment(R.layout.fragment_camera_page) {

    private var pendingCameraUri: Uri? = null
    private var preparedDraftId: String? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            showPhotoPreviewDialog(uri)
        } else {
            pendingCameraUri = null
            Toast.makeText(requireContext(), "Съемка отменена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.takePhotoButton).setOnClickListener {
            launchCamera()
        }

        view.findViewById<View>(R.id.openWardrobeFromCameraButton).setOnClickListener {
            openAllItems()
        }
    }

    private fun launchCamera() {
        val uri = createCameraImageUri()
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun createCameraImageUri(): Uri {
        val cameraDir = File(requireContext().cacheDir, "camera").apply {
            mkdirs()
        }
        val imageFile = File.createTempFile("wardrobe_camera_", ".jpg", cameraDir)
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )
    }

    private fun showPhotoPreviewDialog(uri: Uri) {
        val container = FrameLayout(requireContext()).apply {
            val pad = 16.dp
            setPadding(pad, pad, pad, pad)
        }

        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.45f).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            load(uri) {
                crossfade(true)
            }
        }

        container.addView(imageView)

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить вещь?")
            .setView(container)
            .setNegativeButton("Отмена") { dialog, _ ->
                pendingCameraUri = null
                dialog.dismiss()
            }
            .setNeutralButton("Снять заново") { dialog, _ ->
                dialog.dismiss()
                launchCamera()
            }
            .setPositiveButton("Принять") { dialog, _ ->
                dialog.dismiss()
                uploadAndPrepare(uri)
            }
            .show()
    }

    private fun uploadAndPrepare(uri: Uri) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Загрузка фото...", Toast.LENGTH_SHORT).show()

                val response = RetrofitClient.api.prepareItem(uriToMultipart(uri))
                preparedDraftId = response.draft_id

                if (response.items.isEmpty()) {
                    Toast.makeText(requireContext(), "Вещи не найдены", Toast.LENGTH_LONG).show()
                    return@launch
                }

                showPreparedCandidatesDialog(response.items, 0)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Ошибка загрузки: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun uriToMultipart(uri: Uri): MultipartBody.Part {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть фото")

        val tempFile = File.createTempFile("camera_upload_", ".jpg", requireContext().cacheDir)
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
    }

    private fun showPreparedCandidatesDialog(
        items: List<DraftCandidateDto>,
        startIndex: Int
    ) {
        if (items.isEmpty()) return

        DetectedItemsReviewDialogFragment().apply {
            this.items = items
            this.startIndex = startIndex
            onConfirmSelected = { candidateIds ->
                confirmPreparedItems(candidateIds)
            }
        }.show(childFragmentManager, "DetectedItemsReviewDialog")
    }

    private fun confirmPreparedItems(candidateIds: List<String>) {
        val draftId = preparedDraftId ?: run {
            Toast.makeText(requireContext(), "Нет draft_id", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = ConfirmRequest(
                    draft_id = draftId,
                    selected_items = candidateIds.map { candidateId ->
                        ConfirmSelectedItem(candidate_id = candidateId)
                    }
                )

                val response = RetrofitClient.api.confirmItem(request)
                WardrobeContainer.addConfirmedItems(requireContext(), response.items)
                WardrobeSyncer.syncFromBackend(requireContext())
                Toast.makeText(
                    requireContext(),
                    "Сохранено вещей: ${response.items.size}",
                    Toast.LENGTH_LONG
                ).show()
                openAllItems()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Ошибка сохранения: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openAllItems() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, ClothingItemsFragment.newInstance())
            .addToBackStack("camera_all_items")
            .commit()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
