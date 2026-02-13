package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PdfPasswordStoreTest {
    @Test
    fun `remember and retrieve password for asset`() {
        val store = PdfPasswordStore()

        store.rememberPassword(assetId = "asset-1", password = "secret")

        assertEquals("secret", store.getPassword("asset-1"))
    }

    @Test
    fun `blank passwords are not stored`() {
        val store = PdfPasswordStore()

        store.rememberPassword(assetId = "asset-1", password = "")

        assertNull(store.getPassword("asset-1"))
    }

    @Test
    fun `forget removes stored password`() {
        val store = PdfPasswordStore()
        store.rememberPassword(assetId = "asset-1", password = "secret")

        store.forgetPassword("asset-1")

        assertNull(store.getPassword("asset-1"))
    }
}
