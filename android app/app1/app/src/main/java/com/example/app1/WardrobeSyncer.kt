package com.example.app1

import android.content.Context

object WardrobeSyncer {
    suspend fun syncFromBackend(context: Context): Result<Int> =
        runCatching {
            val response = RetrofitClient.api.getItems()
            WardrobeContainer.syncRemoteItems(context, response.items)
            response.items.size
        }
}
