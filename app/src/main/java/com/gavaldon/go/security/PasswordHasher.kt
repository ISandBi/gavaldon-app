package com.gavaldon.go.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * OWASP M10: PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes por usuario y 600 000 iteraciones
 * (recomendación OWASP Password Storage Cheat Sheet). Formato: pbkdf2$iteraciones$salBase64$hashBase64.
 * Comparación en tiempo constante (OWASP M3: sin filtración por tiempos).
 * Nota: en la arquitectura final el hash se calcula y guarda en el servidor, no en el teléfono.
 */
object PasswordHasher {
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"
    private const val SEPARATOR = '$'
    private val random = SecureRandom()

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(password, salt, ITERATIONS)
        val b64 = Base64.getEncoder()
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            b64.encodeToString(salt),
            b64.encodeToString(derived)
        ).joinToString(SEPARATOR.toString())
    }

    fun verify(password: CharArray, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 1) return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = derive(password, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
