package com.gavaldon.go.domain

import java.time.YearMonth

enum class CardBrand(val label: String, val cvvLength: Int, val panLength: Int) {
    VISA("Visa", 3, 16),
    MASTERCARD("Mastercard", 3, 16),
    AMEX("American Express", 4, 15),
    UNKNOWN("Tarjeta", 3, 16)
}

/**
 * Validación de entrada de tarjeta (OWASP M4: validar siempre en cliente Y servidor).
 * Cada función devuelve el mensaje de error (que DESCRIBE el problema, ET-UI-001 7.3) o null si es válido.
 * Código puro de Kotlin/JVM: se prueba con tests unitarios.
 */
object CardValidator {

    fun digitsOnly(input: CharSequence): String = buildString {
        for (c in input) if (c in '0'..'9') append(c)
    }

    fun brandOf(digits: String): CardBrand {
        if (digits.isEmpty()) return CardBrand.UNKNOWN
        if (digits.startsWith("34") || digits.startsWith("37")) return CardBrand.AMEX
        if (digits.startsWith("4")) return CardBrand.VISA
        if (digits.length >= 2) {
            val two = digits.substring(0, 2).toInt()
            if (two in 51..55) return CardBrand.MASTERCARD
        }
        if (digits.length >= 4) {
            val four = digits.substring(0, 4).toInt()
            if (four in 2221..2720) return CardBrand.MASTERCARD
        }
        return CardBrand.UNKNOWN
    }

    /** Algoritmo de Luhn sobre una cadena de solo dígitos. */
    fun luhnValid(digits: String): Boolean {
        if (digits.length < 12 || digits.any { it !in '0'..'9' }) return false
        var sum = 0
        var doubleIt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (doubleIt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            doubleIt = !doubleIt
        }
        return sum % 10 == 0
    }

    fun validatePan(raw: CharSequence): String? {
        val digits = digitsOnly(raw)
        if (digits.isEmpty()) return "Ingresa el número de tarjeta."
        val brand = brandOf(digits)
        if (brand == CardBrand.UNKNOWN) {
            return "Solo se aceptan tarjetas Visa, Mastercard y American Express."
        }
        if (digits.length != brand.panLength) {
            return "El número de una tarjeta ${brand.label} debe tener ${brand.panLength} dígitos."
        }
        if (!luhnValid(digits)) return "El número de tarjeta no es válido. Revisa los dígitos."
        return null
    }

    private val EXPIRY = Regex("^(0[1-9]|1[0-2])/(\\d{2})$")

    /** @return (mes, año de 4 dígitos) o null si el formato no es MM/AA. */
    fun parseExpiry(raw: CharSequence): Pair<Int, Int>? {
        val m = EXPIRY.matchEntire(raw.toString().trim()) ?: return null
        return m.groupValues[1].toInt() to (2000 + m.groupValues[2].toInt())
    }

    fun validateExpiry(raw: CharSequence, now: YearMonth = YearMonth.now()): String? {
        val parsed = parseExpiry(raw) ?: return "Usa el formato MM/AA, por ejemplo 08/29."
        val expiry = YearMonth.of(parsed.second, parsed.first)
        if (expiry.isBefore(now)) return "La tarjeta está vencida. Usa otra tarjeta."
        if (expiry.isAfter(now.plusYears(20))) return "La fecha de vencimiento no es válida."
        return null
    }

    fun validateCvv(raw: CharSequence, brand: CardBrand): String? {
        val s = raw.toString()
        if (s.isEmpty() || s.any { it !in '0'..'9' } || s.length != brand.cvvLength) {
            return "El CVV debe tener ${brand.cvvLength} dígitos."
        }
        return null
    }

    private val HOLDER = Regex("^[\\p{L} .'-]{2,60}$")

    fun validateHolder(raw: CharSequence): String? {
        val s = raw.toString().trim()
        if (s.length < 2) return "Escribe el nombre como aparece en la tarjeta."
        if (!HOLDER.matches(s)) return "El nombre solo puede tener letras, espacios, punto, guion y apóstrofo."
        return null
    }

    /** "4111111111111111" -> "4111 1111 1111 1111" (Amex: 4-6-5). Solo para mostrar mientras se escribe. */
    fun groupForDisplay(digits: String): String {
        val brand = brandOf(digits)
        val groups = if (brand == CardBrand.AMEX) listOf(4, 6, 5) else listOf(4, 4, 4, 4, 4)
        val sb = StringBuilder()
        var index = 0
        for (g in groups) {
            if (index >= digits.length) break
            if (sb.isNotEmpty()) sb.append(' ')
            val end = minOf(index + g, digits.length)
            sb.append(digits, index, end)
            index = end
        }
        return sb.toString()
    }

    /** PCI DSS 3.4: como máximo se muestran los últimos 4 dígitos. */
    fun maskLast4(last4: String): String = "•••• $last4"
}
