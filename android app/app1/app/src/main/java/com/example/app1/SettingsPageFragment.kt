package com.example.app1

import android.app.AlertDialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsPageFragment : Fragment(R.layout.fragment_settings_page) {

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var isWeatherSettingsExpanded = false
    private var citySuggestionsJob: Job? = null
    private var suppressCityTextWatcher = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        Toast.makeText(
            requireContext(),
            if (granted) "Геопозиция для погоды включена" else "Геопозиция недоступна",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val weatherSwitch = view.findViewById<SwitchMaterial>(R.id.weatherSwitch)
        val notificationsSwitch = view.findViewById<SwitchMaterial>(R.id.notificationsSwitch)
        val darkThemeSwitch = view.findViewById<SwitchMaterial>(R.id.darkThemeSwitch)

        weatherSwitch.isChecked = WeatherPreferences.isEnabled(requireContext())
        notificationsSwitch.isChecked = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        darkThemeSwitch.isChecked = prefs.getBoolean(KEY_DARK_THEME, false)

        setupWeatherSettings(view, weatherSwitch)

        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply()
        }

        darkThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_THEME, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        view.findViewById<View>(R.id.openWardrobeRow).setOnClickListener {
            openAllItems()
        }

        view.findViewById<View>(R.id.syncWardrobeRow).setOnClickListener {
            syncWardrobe()
        }

        view.findViewById<View>(R.id.clearCacheRow).setOnClickListener {
            confirmClearWardrobe()
        }

        view.findViewById<View>(R.id.bodyParamsRow).setOnClickListener {
            openBodyParams()
        }

        view.findViewById<View>(R.id.stylePrefsRow).setOnClickListener {
            Toast.makeText(requireContext(), "Предпочтения стиля пока в разработке", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.languageRow).setOnClickListener {
            Toast.makeText(requireContext(), "Сейчас доступен русский язык", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.logoutRow).setOnClickListener {
            confirmLogout()
        }

        updateWardrobeCount()
        updateProfile()
        loadBodyProfileSummary()
    }

    override fun onResume() {
        super.onResume()
        updateWardrobeCount()
        updateProfile()
        loadBodyProfileSummary()
        view?.let(::renderWeatherSettings)
    }

    private fun setupWeatherSettings(view: View, weatherSwitch: SwitchMaterial) {
        val modeGroup = view.findViewById<RadioGroup>(R.id.weatherModeGroup)
        val geoRadio = view.findViewById<RadioButton>(R.id.weatherGeoRadio)
        val cityRadio = view.findViewById<RadioButton>(R.id.weatherCityRadio)
        val cityInput = view.findViewById<TextInputEditText>(R.id.weatherCityEditText)
        val cityInputLayout = view.findViewById<TextInputLayout>(R.id.weatherCityInputLayout)
        val saveCityButton = view.findViewById<Button>(R.id.saveWeatherCityButton)

        cityInput.setText(WeatherPreferences.getCity(requireContext()))
        geoRadio.isChecked = WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_GEO
        cityRadio.isChecked = WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_CITY
        renderWeatherSettings(view)

        weatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            WeatherPreferences.setEnabled(requireContext(), isChecked)
            isWeatherSettingsExpanded = false
            clearCitySuggestions(view)
            renderWeatherSettings(view)
            if (isChecked && WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_GEO) {
                requestLocationPermissionIfNeeded()
            }
        }

        view.findViewById<View>(R.id.weatherAdviceRow).setOnClickListener {
            if (!WeatherPreferences.isEnabled(requireContext())) {
                weatherSwitch.isChecked = true
                isWeatherSettingsExpanded = true
            } else {
                isWeatherSettingsExpanded = !isWeatherSettingsExpanded
            }
            clearCitySuggestions(view)
            renderWeatherSettings(view)
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.weatherCityRadio) {
                WeatherPreferences.MODE_CITY
            } else {
                WeatherPreferences.MODE_GEO
            }
            WeatherPreferences.setMode(requireContext(), mode)
            isWeatherSettingsExpanded = true
            clearCitySuggestions(view)
            renderWeatherSettings(view)
            if (mode == WeatherPreferences.MODE_GEO && WeatherPreferences.isEnabled(requireContext())) {
                requestLocationPermissionIfNeeded()
            }
        }

        cityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressCityTextWatcher) return
                cityInputLayout.error = null
                scheduleCitySuggestions(view, s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        saveCityButton.setOnClickListener {
            val city = cityInput.text?.toString().orEmpty().trim()
            if (city.isBlank()) {
                cityInputLayout.error = "Введите город"
                return@setOnClickListener
            }

            cityInputLayout.error = null
            WeatherPreferences.setCity(requireContext(), city)
            WeatherPreferences.setMode(requireContext(), WeatherPreferences.MODE_CITY)
            WeatherPreferences.setEnabled(requireContext(), true)
            weatherSwitch.isChecked = true
            cityRadio.isChecked = true
            clearCitySuggestions(view)
            renderWeatherSettings(view)
            Toast.makeText(requireContext(), "Город для погоды сохранен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderWeatherSettings(view: View) {
        val enabled = WeatherPreferences.isEnabled(requireContext())
        val cityMode = WeatherPreferences.getMode(requireContext()) == WeatherPreferences.MODE_CITY
        val panel = view.findViewById<View>(R.id.weatherSettingsPanel)
        val cityInputLayout = view.findViewById<TextInputLayout>(R.id.weatherCityInputLayout)
        val saveCityButton = view.findViewById<Button>(R.id.saveWeatherCityButton)
        val suggestionsContainer = view.findViewById<LinearLayout>(R.id.weatherCitySuggestionsContainer)

        panel.visibility = if (enabled && isWeatherSettingsExpanded) View.VISIBLE else View.GONE
        cityInputLayout.visibility = if (enabled && isWeatherSettingsExpanded && cityMode) View.VISIBLE else View.GONE
        saveCityButton.visibility = if (enabled && isWeatherSettingsExpanded && cityMode) View.VISIBLE else View.GONE
        if (!enabled || !isWeatherSettingsExpanded || !cityMode) {
            suggestionsContainer.visibility = View.GONE
        }

        view.findViewById<RadioButton>(R.id.weatherGeoRadio).isChecked = !cityMode
        view.findViewById<RadioButton>(R.id.weatherCityRadio).isChecked = cityMode
        view.findViewById<TextView>(R.id.weatherSubtitleText).text = weatherSettingsSummary(enabled, cityMode)
    }

    private fun weatherSettingsSummary(enabled: Boolean, cityMode: Boolean): String {
        if (!enabled) return "Погода не учитывается"

        if (!cityMode) return "По геопозиции"

        val city = WeatherPreferences.getCity(requireContext()).ifBlank { null }
        return city?.let { "Город: $it" } ?: "Укажите город"
    }

    private fun scheduleCitySuggestions(view: View, query: String) {
        citySuggestionsJob?.cancel()
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) {
            clearCitySuggestions(view)
            return
        }

        citySuggestionsJob = lifecycleScope.launch {
            delay(350)
            runCatching {
                WeatherApiClient.geocodingApi.searchCity(
                    name = trimmedQuery,
                    count = 6
                ).results
            }.onSuccess { places ->
                renderCitySuggestions(view, places)
            }.onFailure {
                clearCitySuggestions(view)
            }
        }
    }

    private fun renderCitySuggestions(view: View, places: List<OpenMeteoPlace>) {
        val container = view.findViewById<LinearLayout>(R.id.weatherCitySuggestionsContainer)
        container.removeAllViews()

        if (places.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        val cityInput = view.findViewById<TextInputEditText>(R.id.weatherCityEditText)
        val cityRadio = view.findViewById<RadioButton>(R.id.weatherCityRadio)
        val cityInputLayout = view.findViewById<TextInputLayout>(R.id.weatherCityInputLayout)

        places.forEach { place ->
            val row = TextView(requireContext()).apply {
                text = place.displayName()
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    val selectedCity = place.displayName()
                    suppressCityTextWatcher = true
                    cityInput.setText(selectedCity)
                    cityInput.setSelection(selectedCity.length)
                    suppressCityTextWatcher = false
                    cityInputLayout.error = null
                    WeatherPreferences.setCity(requireContext(), selectedCity)
                    WeatherPreferences.setMode(requireContext(), WeatherPreferences.MODE_CITY)
                    WeatherPreferences.setEnabled(requireContext(), true)
                    cityRadio.isChecked = true
                    clearCitySuggestions(view)
                    renderWeatherSettings(view)
                }
            }
            container.addView(row)
        }

        container.visibility = View.VISIBLE
    }

    private fun clearCitySuggestions(view: View) {
        citySuggestionsJob?.cancel()
        view.findViewById<LinearLayout>(R.id.weatherCitySuggestionsContainer)?.let { container ->
            container.removeAllViews()
            container.visibility = View.GONE
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (WeatherLocationProvider.hasLocationPermission(requireContext())) return

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onDestroyView() {
        citySuggestionsJob?.cancel()
        super.onDestroyView()
    }

    private fun updateWardrobeCount() {
        val count = WardrobeContainer.getAllItems(requireContext()).size
        view?.findViewById<TextView>(R.id.wardrobeCountText)?.text = "Вещей: $count"
        view?.findViewById<TextView>(R.id.profileSubtitleText)?.text = "В гардеробе $count вещей"
    }

    private fun updateProfile() {
        val name = AuthStorage.getName(requireContext())
        val email = AuthStorage.getEmail(requireContext())
        view?.findViewById<TextView>(R.id.profileNameText)?.text =
            name?.takeIf { it.isNotBlank() } ?: email ?: "Пользователь Helpic"
    }

    private fun loadBodyProfileSummary() {
        lifecycleScope.launch {
            runCatching {
                RetrofitClient.api.getBodyProfile()
            }.onSuccess { profile ->
                view?.findViewById<TextView>(R.id.bodyParamsSubtitleText)?.text =
                    BodyProfileFormatter.summary(profile)
            }
        }
    }

    private fun openBodyParams() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, BodyParamsFragment())
            .addToBackStack("settings_body_params")
            .commit()
    }

    private fun openAllItems() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.framelayout, ClothingItemsFragment.newInstance())
            .addToBackStack("settings_all_items")
            .commit()
    }

    private fun syncWardrobe() {
        lifecycleScope.launch {
            WardrobeSyncer.syncFromBackend(requireContext()).onSuccess { count ->
                updateWardrobeCount()
                Toast.makeText(
                    requireContext(),
                    "Гардероб обновлен: $count",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Не удалось синхронизировать: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmClearWardrobe() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить локальный гардероб?")
            .setMessage("Удалятся только вещи, сохраненные на этом устройстве. Backend-данные не изменятся.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Очистить") { _, _ ->
                WardrobeContainer.clear(requireContext())
                updateWardrobeCount()
                Toast.makeText(requireContext(), "Локальный гардероб очищен", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Выйти из профиля?")
            .setMessage("Текущий токен будет удален с устройства. Локальный кэш гардероба тоже будет очищен.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Выйти") { _, _ ->
                AuthStorage.clear(requireContext())
                WardrobeContainer.clear(requireContext())
                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .show()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
