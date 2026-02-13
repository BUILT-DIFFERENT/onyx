package com.onyx.android.pdf

class PdfPasswordStore {
    private val lock = Any()
    private val passwordsByAssetId = mutableMapOf<String, String>()

    fun rememberPassword(
        assetId: String,
        password: String?,
    ) {
        if (password.isNullOrBlank()) {
            return
        }
        synchronized(lock) {
            passwordsByAssetId[assetId] = password
        }
    }

    fun getPassword(assetId: String): String? {
        return synchronized(lock) {
            passwordsByAssetId[assetId]
        }
    }

    fun forgetPassword(assetId: String) {
        synchronized(lock) {
            passwordsByAssetId.remove(assetId)
        }
    }
}
