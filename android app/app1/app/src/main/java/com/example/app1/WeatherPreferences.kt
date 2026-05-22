package com.example.app1

import android.content.Context

object WeatherPreferences {
    const val MODE_GEO = "geo"
    const val MODE_CITY = "city"

    private const val PREFS_NAME = "app_settings"
    private const val KEY_ENABLED = "weather_enabled"
    private const val KEY_MODE = "weather_mode"
    private const val KEY_CITY = "weather_city"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(scopedKey(context, KEY_ENABLED), false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(scopedKey(context, KEY_ENABLED), enabled).apply()
    }

    fun getMode(context: Context): String =
        prefs(context).getString(scopedKey(context, KEY_MODE), MODE_GEO) ?: MODE_GEO

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(scopedKey(context, KEY_MODE), mode).apply()
    }

    fun getCity(context: Context): String =
        prefs(context).getString(scopedKey(context, KEY_CITY), "").orEmpty()

    fun setCity(context: Context, city: String) {
        prefs(context).edit().putString(scopedKey(context, KEY_CITY), city.trim()).apply()
    }

    fun clearLegacy(context: Context) {
        prefs(context).edit()
            .remove(KEY_ENABLED)
            .remove(KEY_MODE)
            .remove(KEY_CITY)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scopedKey(context: Context, key: String): String {
        val userId = AuthStorage.getUserId(context)?.takeIf { it.isNotBlank() } ?: "guest"
        return "$userId:$key"
    }
}
