package com.example.app1

import android.content.Context

object AuthStorage {
    private const val PREFS_NAME = "auth_storage"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"

    fun save(context: Context, response: AuthResponse) {
        prefs(context).edit()
            .putString(KEY_TOKEN, response.token)
            .putString(KEY_USER_ID, response.user.user_id)
            .putString(KEY_EMAIL, response.user.email)
            .putString(KEY_NAME, response.user.name)
            .apply()
        RetrofitClient.setAuthToken(response.token)
    }

    fun restore(context: Context) {
        RetrofitClient.setAuthToken(getToken(context))
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        RetrofitClient.setAuthToken(null)
    }

    fun isLoggedIn(context: Context): Boolean =
        !getToken(context).isNullOrBlank()

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun getName(context: Context): String? =
        prefs(context).getString(KEY_NAME, null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
