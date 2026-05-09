package com.example.app1

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class BodyParamsFragment : Fragment(R.layout.fragment_body_params) {

    private lateinit var statusText: TextView
    private lateinit var skinToneInput: TextInputEditText
    private lateinit var eyeColorInput: TextInputEditText
    private lateinit var hairColorInput: TextInputEditText
    private lateinit var heightInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var chestInput: TextInputEditText
    private lateinit var waistInput: TextInputEditText
    private lateinit var applyButton: Button
    private lateinit var resetButton: Button

    private val fields: List<TextInputEditText>
        get() = listOf(
            skinToneInput,
            eyeColorInput,
            hairColorInput,
            heightInput,
            weightInput,
            chestInput,
            waistInput
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.bodyParamsStatusText)
        skinToneInput = view.findViewById(R.id.skinToneEditText)
        eyeColorInput = view.findViewById(R.id.eyeColorEditText)
        hairColorInput = view.findViewById(R.id.hairColorEditText)
        heightInput = view.findViewById(R.id.heightEditText)
        weightInput = view.findViewById(R.id.weightEditText)
        chestInput = view.findViewById(R.id.chestEditText)
        waistInput = view.findViewById(R.id.waistEditText)
        applyButton = view.findViewById(R.id.applyBodyParamsButton)
        resetButton = view.findViewById(R.id.resetBodyParamsButton)

        configureInputTypes()

        view.findViewById<View>(R.id.bodyParamsBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        applyButton.setOnClickListener {
            saveBodyProfile()
        }

        resetButton.setOnClickListener {
            confirmReset()
        }

        loadBodyProfile()
    }

    private fun configureInputTypes() {
        val textInputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        skinToneInput.inputType = textInputType
        eyeColorInput.inputType = textInputType
        hairColorInput.inputType = textInputType
    }

    private fun loadBodyProfile() {
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                RetrofitClient.api.getBodyProfile()
            }.onSuccess { profile ->
                renderProfile(profile)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось загрузить параметры тела",
                    Toast.LENGTH_LONG
                ).show()
                statusText.text = "Параметры пока не загружены"
            }
            setBusy(false)
        }
    }

    private fun saveBodyProfile() {
        val request = try {
            collectRequest()
        } catch (error: IllegalArgumentException) {
            Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                RetrofitClient.api.saveBodyProfile(request)
            }.onSuccess { profile ->
                renderProfile(profile)
                Toast.makeText(requireContext(), "Параметры тела сохранены", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось сохранить параметры тела",
                    Toast.LENGTH_LONG
                ).show()
            }
            setBusy(false)
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setTitle("Сбросить параметры тела?")
            .setMessage("Все заполненные значения будут удалены из профиля.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить") { _, _ ->
                resetBodyProfile()
            }
            .show()
    }

    private fun resetBodyProfile() {
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                RetrofitClient.api.resetBodyProfile()
            }.onSuccess { profile ->
                renderProfile(profile)
                Toast.makeText(requireContext(), "Параметры тела сброшены", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: "Не удалось сбросить параметры тела",
                    Toast.LENGTH_LONG
                ).show()
            }
            setBusy(false)
        }
    }

    private fun renderProfile(profile: BodyProfileDto) {
        skinToneInput.setText(profile.skin_tone.orEmpty())
        eyeColorInput.setText(profile.eye_color.orEmpty())
        hairColorInput.setText(profile.hair_color.orEmpty())
        heightInput.setText(profile.height_cm?.toString().orEmpty())
        weightInput.setText(profile.weight_kg?.toString().orEmpty())
        chestInput.setText(profile.chest_cm?.toString().orEmpty())
        waistInput.setText(profile.waist_cm?.toString().orEmpty())
        clearErrors()
        statusText.text = BodyProfileFormatter.summary(profile)
    }

    private fun collectRequest(): BodyProfileRequest {
        clearErrors()

        return BodyProfileRequest(
            skin_tone = textOrNull(skinToneInput),
            eye_color = textOrNull(eyeColorInput),
            hair_color = textOrNull(hairColorInput),
            height_cm = positiveIntOrNull(heightInput, "рост"),
            weight_kg = positiveIntOrNull(weightInput, "вес"),
            chest_cm = positiveIntOrNull(chestInput, "обхват груди"),
            waist_cm = positiveIntOrNull(waistInput, "обхват талии")
        )
    }

    private fun positiveIntOrNull(input: TextInputEditText, fieldName: String): Int? {
        val rawValue = input.text?.toString()?.trim().orEmpty()
        if (rawValue.isBlank()) return null

        val value = rawValue.toIntOrNull()
        if (value == null || value <= 0) {
            input.error = "Введите число"
            input.requestFocus()
            throw IllegalArgumentException("Проверьте поле: $fieldName")
        }

        return value
    }

    private fun textOrNull(input: TextInputEditText): String? {
        return input.text?.toString()?.trim()?.ifBlank { null }
    }

    private fun clearErrors() {
        fields.forEach { it.error = null }
    }

    private fun setBusy(isBusy: Boolean) {
        fields.forEach { it.isEnabled = !isBusy }
        applyButton.isEnabled = !isBusy
        resetButton.isEnabled = !isBusy
    }
}
