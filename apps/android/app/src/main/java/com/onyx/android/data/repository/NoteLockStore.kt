package com.onyx.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class NoteLockStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPasscode(): Boolean = !prefs.getString(KEY_PASSCODE_HASH, null).isNullOrBlank()

    fun verifyPasscode(passcode: String): Boolean {
        val expectedHash = prefs.getString(KEY_PASSCODE_HASH, null)
        val salt = prefs.getString(KEY_PASSCODE_SALT, null)
        if (expectedHash.isNullOrBlank() || salt.isNullOrBlank()) {
            return false
        }
        return expectedHash == computeHash(passcode = passcode, salt = salt)
    }

    fun setPasscode(passcode: String) {
        val salt = newSalt()
        val hash = computeHash(passcode = passcode, salt = salt)
        prefs
            .edit()
            .putString(KEY_PASSCODE_SALT, salt)
            .putString(KEY_PASSCODE_HASH, hash)
            .apply()
    }

    private fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun computeHash(
        passcode: String,
        salt: String,
    ): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val bytes = digest.digest("$salt:$passcode".toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        private const val PREFS_NAME = "onyx_note_lock_store"
        private const val KEY_PASSCODE_HASH = "note_lock_passcode_hash"
        private const val KEY_PASSCODE_SALT = "note_lock_passcode_salt"
        private const val HASH_ALGORITHM = "SHA-256"
        private const val SALT_BYTES = 32
    }
}
