package com.example.app1

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class WardrobePageFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingImageUri: Uri? = null
    private lateinit var jumpRunnable: Runnable
    private var preparedDraftId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wardrobe_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val addButton = view.findViewById<ImageButton>(R.id.add_button_wardrobe)
        val allItemsCard = view.findViewById<View>(R.id.card_all_items)
        val favoriteItemsCard = view.findViewById<View>(R.id.card_favorite_items)
        val searchItemsCard = view.findViewById<View>(R.id.card_search_items)

        allItemsCard.setOnClickListener {
            openAllItems()
        }

        favoriteItemsCard.setOnClickListener {
            openFavoriteItems()
        }

        searchItemsCard.setOnClickListener {
            openSearchItems()
        }

        updateWardrobeCounts(view)
        syncWardrobeItems()

        addButton.setOnClickListener {
            val dialogFragment = CustomDialogFragment().apply {
                actions = object : CustomDialogFragment.Actions {
                    override fun onPickFromGallery() {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }

                    override fun onTakePhoto() {
                        openCamera()
                    }

                    override fun onImportByLink() {
                        showLinkImportDialog()
                    }
                }
            }

            dialogFragment.show(childFragmentManager, "CustomDialogFragment")
        }

        jumpRunnable = object : Runnable {
            override fun run() {
                startJumpAnimation(addButton)
                handler.postDelayed(this, 2000)
            }
        }

        handler.postDelayed(jumpRunnable, 2000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(jumpRunnable)
    }

    override fun onResume() {
        super.onResume()
        view?.let(::updateWardrobeCounts)
    }

    private fun openAllItems() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, ClothingItemsFragment.newInstance())
            .addToBackStack("all_items")
            .commit()
    }

    private fun openFavoriteItems() {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.framelayout,
                ClothingItemsFragment.newInstance(
                    title = "Избранное",
                    favoriteOnly = true
                )
            )
            .addToBackStack("favorite_items")
            .commit()
    }

    private fun openSearchItems() {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.framelayout,
                ClothingItemsFragment.newInstance(
                    title = "Поиск вещи",
                    searchMode = true
                )
            )
            .addToBackStack("search_items")
            .commit()
    }

    private fun openCamera() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, CameraPageFragment())
            .addToBackStack("camera_add_item")
            .commit()
    }

    private fun updateWardrobeCounts(view: View) {
        val allCount = WardrobeContainer.getAllItems(requireContext()).size
        val favoriteCount = WardrobeContainer.getFavoriteItems(requireContext()).size
        view.findViewById<TextView>(R.id.all_items_count).text = "Вещей: $allCount"
        view.findViewById<TextView>(R.id.favorite_items_count).text = "Вещей: $favoriteCount"
    }

    private fun syncWardrobeItems() {
        lifecycleScope.launch {
            WardrobeSyncer.syncFromBackend(requireContext()).onSuccess {
                view?.let(::updateWardrobeCounts)
            }
        }
    }

    private fun uriToMultipart(uri: Uri): MultipartBody.Part {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть InputStream")

        val tempFile = File.createTempFile("upload_", ".jpg", requireContext().cacheDir)

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val requestFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())

        return MultipartBody.Part.createFormData(
            "image",
            tempFile.name,
            requestFile
        )
    }

    private fun uploadAndPrepare(uri: Uri) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Загрузка...", Toast.LENGTH_SHORT).show()

                val imagePart = uriToMultipart(uri)
                prepareAndShowCandidates(imagePart)

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

    private suspend fun prepareAndShowCandidates(imagePart: MultipartBody.Part) {
        val response = RetrofitClient.api.prepareItem(imagePart)

        preparedDraftId = response.draft_id

        if (response.items.isEmpty()) {
            Toast.makeText(requireContext(), "Ничего не найдено", Toast.LENGTH_LONG).show()
            return
        }

        showPreparedCandidatesDialog(response.items, 0)
    }

    private fun showLinkImportDialog() {
        val contentView = layoutInflater.inflate(R.layout.dialog_link_import, null)
        val inputLayout = contentView.findViewById<TextInputLayout>(R.id.linkInputLayout)
        val editText = contentView.findViewById<TextInputEditText>(R.id.linkEditText)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Загрузка по ссылке")
            .setView(contentView)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Найти", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = editText.text?.toString().orEmpty().trim()
                if (query.isBlank()) {
                    inputLayout.error = "Введите ссылку или артикул"
                    return@setOnClickListener
                }

                inputLayout.error = null
                dialog.dismiss()
                resolveMarketplaceLink(query)
            }
        }

        dialog.show()
    }

    private fun resolveMarketplaceLink(query: String) {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Ищу фото товара...", Toast.LENGTH_SHORT).show()

            runCatching {
                MarketplaceLinkResolver.resolve(query)
            }.onSuccess { importedImage ->
                showImportedLinkPreviewDialog(importedImage)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось обработать ссылку",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showImportedLinkPreviewDialog(importedImage: ImportedMarketplaceImage) {
        val container = FrameLayout(requireContext()).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.42f).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            load(importedImage.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_wardrobe)
                error(R.drawable.ic_wardrobe)
            }
        }

        container.addView(imageView)

        AlertDialog.Builder(requireContext())
            .setTitle("Фото товара найдено")
            .setMessage("Проверьте, что на фото нужная вещь. После подтверждения отправлю ее на распознавание.")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Распознать") { dialog, _ ->
                dialog.dismiss()
                uploadImportedImage(importedImage)
            }
            .show()
    }

    private fun uploadImportedImage(importedImage: ImportedMarketplaceImage) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Загрузка по ссылке...", Toast.LENGTH_SHORT).show()
                prepareAndShowCandidates(importedImage.toMultipart())
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

    private fun ImportedMarketplaceImage.toMultipart(): MultipartBody.Part {
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", fileName, requestBody)
    }

    private fun prepareItemsFromUri(uri: Uri) {
        uploadAndPrepare(uri)
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

    private fun loadWardrobeItems() {
        val savedItemsCount = WardrobeContainer.getAllItems(requireContext()).size
        view?.let(::updateWardrobeCounts)
        Toast.makeText(
            requireContext(),
            "Всего вещей в гардеробе: $savedItemsCount",
            Toast.LENGTH_SHORT
        ).show()
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
                    selected_items = candidateIds.map {
                        ConfirmSelectedItem(
                            candidate_id = it,
                            user_overrides = null
                        )
                    }
                )

                val response = RetrofitClient.api.confirmItem(request)
                WardrobeContainer.addConfirmedItems(requireContext(), response.items)
                WardrobeSyncer.syncFromBackend(requireContext())
                updateWardrobeCounts(requireView())

                Toast.makeText(
                    requireContext(),
                    "Сохранено вещей: ${response.items.size}",
                    Toast.LENGTH_LONG
                ).show()

                loadWardrobeItems()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Ошибка confirm: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showMiniPreviewDialog(uri: Uri) {
        val container = FrameLayout(requireContext()).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val imageHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt()

        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                imageHeight
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        imageView.load(uri) {
            crossfade(true)
        }

        container.addView(imageView)

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить вещь?")
            .setView(container)
            .setNegativeButton("Отмена") { dialog, _ ->
                pendingImageUri = null
                dialog.dismiss()
            }
            .setNeutralButton("Выбрать другое") { dialog, _ ->
                dialog.dismiss()
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .setPositiveButton("Принять") { dialog, _ ->
                dialog.dismiss()
                pendingImageUri?.let { acceptedUri ->
                    prepareItemsFromUri(acceptedUri)
                }
                pendingImageUri = null
            }
            .show()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(requireContext(), "Выбор отменён", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        pendingImageUri = uri
        showMiniPreviewDialog(uri)
    }

    private fun startJumpAnimation(view: View) {
        val up = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -50f)
        val down = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -30f, 0f)

        up.duration = 300
        down.duration = 300

        AnimatorSet().apply {
            playSequentially(up, down)
            interpolator = OvershootInterpolator()
            start()
        }
    }
}
