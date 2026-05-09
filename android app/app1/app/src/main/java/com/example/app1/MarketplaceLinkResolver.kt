package com.example.app1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ImportedMarketplaceImage(
    val imageUrl: String,
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

object MarketplaceLinkResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolve(query: String): ImportedMarketplaceImage = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) {
            "Введите ссылку или артикул"
        }

        val productUrl = normalizedQuery.toProductUrl()
        val candidates = linkedSetOf<String>()

        candidates.addAll(productUrl.wildberriesImageCandidates())

        downloadImage(productUrl)?.let {
            return@withContext it
        }

        fetchHtml(productUrl)?.let { html ->
            candidates.addAll(extractImageUrls(html, productUrl))
        }

        for (candidate in candidates) {
            downloadImage(candidate)?.let {
                return@withContext it
            }
        }

        throw IllegalStateException("Не удалось найти изображение товара. Попробуйте прямую ссылку на фото.")
    }

    private fun String.toProductUrl(): String {
        val value = trim()
        if (value.all(Char::isDigit)) {
            return "https://www.wildberries.ru/catalog/$value/detail.aspx"
        }

        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://$value"
        }
    }

    private fun String.wildberriesImageCandidates(): List<String> {
        val article = Regex("""(?:catalog/)?(\d{5,})(?:/|$|\?)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?: takeIf { it.all(Char::isDigit) }
            ?: return emptyList()

        val nm = article.toLongOrNull() ?: return emptyList()
        val vol = nm / 100000
        val part = nm / 1000
        val paths = listOf("images/big/1.webp", "images/c516x688/1.webp", "images/tm/1.webp")
        val hosts = (1..20).map { index -> "basket-${index.toString().padStart(2, '0')}.wbbasket.ru" }

        return hosts.flatMap { host ->
            paths.map { path ->
                "https://$host/vol$vol/part$part/$nm/$path"
            }
        }
    }

    private fun fetchHtml(url: String): String? {
        val response = runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
            ).execute()
        }.getOrNull() ?: return null

        response.use {
            if (!it.isSuccessful) return null
            val contentType = it.body?.contentType()?.toString().orEmpty()
            if (!contentType.contains("html", ignoreCase = true) &&
                !contentType.contains("text", ignoreCase = true)
            ) {
                return null
            }
            return it.body?.string()
        }
    }

    private fun extractImageUrls(html: String, baseUrl: String): List<String> {
        val urls = linkedSetOf<String>()

        Regex("""<meta[^>]+>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.value }
            .filter { tag ->
                tag.contains("og:image", ignoreCase = true) ||
                    tag.contains("twitter:image", ignoreCase = true) ||
                    tag.contains("image_src", ignoreCase = true)
            }
            .forEach { tag ->
                extractAttribute(tag, "content")?.let { urls.add(resolveUrl(baseUrl, it)) }
                extractAttribute(tag, "href")?.let { urls.add(resolveUrl(baseUrl, it)) }
            }

        Regex(""""image"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .forEach { urls.add(resolveUrl(baseUrl, it.replace("\\/", "/"))) }

        Regex("""https?://[^"'\s<>]+?\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.value }
            .forEach { urls.add(it) }

        return urls.toList()
    }

    private fun extractAttribute(tag: String, attribute: String): String? {
        return Regex("""$attribute\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun resolveUrl(baseUrl: String, rawUrl: String): String {
        val value = rawUrl.trim()
        if (value.startsWith("//")) return "https:$value"
        if (value.startsWith("http://") || value.startsWith("https://")) return value

        return runCatching {
            URI(baseUrl).resolve(value).toString()
        }.getOrDefault(value)
    }

    private fun downloadImage(url: String): ImportedMarketplaceImage? {
        val response = runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()
            ).execute()
        }.getOrNull() ?: return null

        response.use {
            if (!it.isSuccessful) return null
            val body = it.body ?: return null
            val contentType = body.contentType()?.toString().orEmpty()
            val bytes = body.bytes()

            if (!contentType.startsWith("image/", ignoreCase = true) && !url.looksLikeImageUrl()) {
                return null
            }

            if (bytes.size < 1024) return null

            val mimeType = contentType.takeIf { type -> type.startsWith("image/", ignoreCase = true) }
                ?.substringBefore(';')
                ?: url.mimeTypeFromExtension()

            return ImportedMarketplaceImage(
                imageUrl = url,
                bytes = bytes,
                mimeType = mimeType,
                fileName = "marketplace_item.${mimeType.extensionFromMimeType()}"
            )
        }
    }

    private fun String.looksLikeImageUrl(): Boolean =
        lowercase(Locale.ROOT).substringBefore('?').let { url ->
            url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".webp")
        }

    private fun String.mimeTypeFromExtension(): String =
        when (lowercase(Locale.ROOT).substringBefore('?').substringAfterLast('.', "")) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

    private fun String.extensionFromMimeType(): String =
        when (lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
}
