package com.gavaldon.go.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacén clave/valor donde TODO valor se cifra con [KeystoreCipher] antes de tocar el disco.
 * Si un valor no se puede descifrar (alterado o llave invalidada) se trata como inexistente.
 */
class SecureStore(context: Context, name: String) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            KeystoreCipher.decrypt(stored)
        } catch (e: Exception) {
            null
        }
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, KeystoreCipher.encrypt(value)).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = getString(key)?.toLongOrNull() ?: default

    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
