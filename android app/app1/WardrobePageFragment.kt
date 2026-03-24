package com.example.app1

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.widget.ImageView
import coil.load
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream


class WardrobePageFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingImageUri: Uri? = null
    private lateinit var jumpRunnable: Runnable
    private var preparedDraftId: String? = null
    private var preparedNormalizedImageUrl: String? = null

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

        addButton.setOnClickListener {
            val dialogFragment = CustomDialogFragment().apply {
                actions = object : CustomDialogFragment.Actions {
                    override fun onPickFromGallery() {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }

                    override fun onTakePhoto() {
                        Toast.makeText(requireContext(), "Сделать фото — скоро", Toast.LENGTH_SHORT).show()
                    }

                    override fun onImportByLink() {
                        Toast.makeText(requireContext(), "Загрузка по ссылке — скоро", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialogFragment.show(childFragmentManager, "CustomDialogFragment")
        }

        jumpRunnable = object : Runnable {
            override fun run() {
                startJumpAnimation(addButton)
                handler.postDelayed(this, 2000) // каждые 2 секунды
            }
        }


        handler.postDelayed(jumpRunnable, 2000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(jumpRunnable)
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

        val requestFile = tempFile
            .asRequestBody("image/*".toMediaTypeOrNull())

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
                val response = RetrofitClient.api.prepareItem(imagePart)

                preparedDraftId = response.draft_id
                preparedNormalizedImageUrl = response.normalized_image_url

                showPreparedPreviewDialog(response)

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

    private fun addToTshirtsStub(uri: Uri) {
        uploadAndPrepare(uri)
    }

    private fun confirmPreparedItem() {
        val draftId = preparedDraftId ?: run {
            Toast.makeText(requireContext(), "Нет draft_id", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = ConfirmRequest(
                    draft_id = draftId,
                    user_overrides = mapOf("category" to "top"),
                    tags = emptyList()
                )

                val response = RetrofitClient.api.confirmItem(request)

                Toast.makeText(
                    requireContext(),
                    "Вещь добавлена: ${response.item_id}",
                    Toast.LENGTH_LONG
                ).show()

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

        val imageHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt() // 45% высоты экрана

        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                imageHeight
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Загружаем картинку
        imageView.load(uri) {
            crossfade(true)
            // Если хочешь дебаг: покажи заглушку/ошибку
            // placeholder(R.drawable.placeholder)
            // error(R.drawable.error)
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
                    addToTshirtsStub(acceptedUri)
                }
                pendingImageUri = null
            }
            .show()
    }

    private fun showPreparedPreviewDialog(response: PrepareResponse) {
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

        imageView.load(response.normalized_image_url) {
            crossfade(true)
        }

        container.addView(imageView)

        val categoryText = response.attributes.category ?: "Не определено"

        AlertDialog.Builder(requireContext())
            .setTitle("Результат анализа")
            .setMessage("Категория: $categoryText")
            .setView(container)
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Добавить") { dialog, _ ->
                dialog.dismiss()
                confirmPreparedItem()
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

//    private val pickImageLauncher = registerForActivityResult(
//        ActivityResultContracts.PickVisualMedia()
//    ) { uri: Uri? ->
//        try {
//            if (uri == null) {
//                Toast.makeText(requireContext(), "Выбор отменён", Toast.LENGTH_SHORT).show()
//                return@registerForActivityResult
//            }
//
//            Toast.makeText(requireContext(), "URI получен: $uri", Toast.LENGTH_LONG).show()
//            pendingImageUri = uri
//
//            // ВРЕМЕННО не открываем preview
//            // showMiniPreviewDialog(uri)
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }


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
