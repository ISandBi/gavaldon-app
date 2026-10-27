package com.gavaldon.go.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * OWASP M10 (Insufficient Cryptography) / M9 (Insecure Data Storage).
 * AES-256-GCM con la llave dentro del Android Keystore (no exportable, respaldada por hardware cuando existe).
 * IV aleatorio de 12 bytes por cada cifrado, almacenado junto al texto cifrado. GCM autentica los datos:
 * si alguien altera el archivo, el descifrado falla en lugar de devolver basura.
 */
object KeystoreCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "gavaldon_go_master_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    @Synchronized
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    /** @throws java.security.GeneralSecurityException si el dato fue alterado o la llave ya no existe. */
    fun decrypt(encoded: String): String {
        val all = Base64.getDecoder().decode(encoded)
        require(all.size > IV_LENGTH) { "Dato cifrado inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH))
        val plain = cipher.doFinal(all, IV_LENGTH, all.size - IV_LENGTH)
        return String(plain, StandardCharsets.UTF_8)
    }
}
