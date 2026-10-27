package com.gavaldon.go.domain

import java.math.BigDecimal

/** Validadores de formularios de cuenta y de monto. Devuelven mensaje de error o null. */
object InputValidator {

    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,190}\\.[A-Za-z]{2,24}$")
    private val NAME = Regex("^[\\p{L} .'\\-]{2,60}$")

    const val MIN_AMOUNT = 50
    const val MAX_AMOUNT = 2000

    fun validateName(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return "Escribe tu nombre."
        if (!NAME.matches(s)) return "Usa de 2 a 60 letras. Se permiten espacios, punto, guion y apóstrofo."
        return null
    }

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun validateEmail(raw: String): String? {
        val s = normalizeEmail(raw)
        if (s.isEmpty()) return "Escribe tu correo electrónico."
        if (s.length > 254 || !EMAIL.matches(s)) return "El correo no tiene un formato válido, por ejemplo nombre@dominio.com."
        return null
    }

    /** Devuelve solo los 10 dígitos o null si el teléfono no es válido. */
    fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it in '0'..'9' }
        val national = if (digits.length == 12 && digits.startsWith("52")) digits.substring(2) else digits
        return if (national.length == 10) national else null
    }

    fun validatePhone(raw: String): String? =
        if (normalizePhone(raw) == null) "El teléfono debe tener 10 dígitos." else null

    /** Monto entero en pesos entre MIN_AMOUNT y MAX_AMOUNT. */
    fun parseAmount(raw: String): BigDecimal? {
        val s = raw.trim()
        if (s.isEmpty() || s.length > 4 || s.any { it !in '0'..'9' }) return null
        val v = s.toInt()
        return if (v in MIN_AMOUNT..MAX_AMOUNT) BigDecimal(v) else null
    }

    fun validateAmount(raw: String): String? =
        if (parseAmount(raw) == null) "Ingresa un monto entero entre \$$MIN_AMOUNT y \$$MAX_AMOUNT." else null
}

/** PCI DSS 4.0 req. 8.3.6: mínimo 12 caracteres con letras y números. */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128

    fun validate(password: CharArray): String? {
        if (password.size < MIN_LENGTH) return "La contraseña debe tener al menos $MIN_LENGTH caracteres."
        if (password.size > MAX_LENGTH) return "La contraseña no puede pasar de $MAX_LENGTH caracteres."
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            return "La contraseña debe combinar letras y números."
        }
        return null
    }
}
