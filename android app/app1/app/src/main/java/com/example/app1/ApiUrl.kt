package com.example.app1

import android.net.Uri

object ApiUrl {
    private val mediaBaseUrl = RetrofitClient.BASE_URL.trimEnd('/')
    private val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0")

    fun resolveMediaUrl(rawUrl: String?): String? {
        val url = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        if (url.startsWith("/media/")) {
            return "$mediaBaseUrl$url"
        }

        if (url.startsWith("media/")) {
            return "$mediaBaseUrl/$url"
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        val path = uri.encodedPath ?: return url

        if (host in localHosts && path.startsWith("/media/")) {
            val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
            return "$mediaBaseUrl$path$query"
        }

        return url
    }
}
