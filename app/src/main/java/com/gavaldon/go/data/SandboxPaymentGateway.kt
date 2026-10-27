package com.gavaldon.go.data

import com.gavaldon.go.domain.CardData
import com.gavaldon.go.domain.CardValidator
import com.gavaldon.go.domain.ChargeRequest
import com.gavaldon.go.domain.ChargeResult
import com.gavaldon.go.domain.PaymentGateway
import com.gavaldon.go.domain.TokenizeResult
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * PASARELA SIMULADA (sandbox). No cobra dinero real.
 *
 * Representa el contrato real: (1) el proveedor convierte la tarjeta en un TOKEN de un solo uso y
 * (2) el cobro se hace con el token, nunca con el número de tarjeta. Cuando GAVALDON elija proveedor,
 * se sustituye esta clase por su SDK (la tarjeta se captura con los campos del proveedor y el PAN ya
 * no pasa por código propio, reduciendo el alcance PCI a SAQ A).
 *
 * Tarjetas de prueba (todas pasan Luhn):
 *   4111 1111 1111 1111  -> aprobada
 *   4000 0000 0000 0002  -> declinada
 *   4000 0000 0000 0259  -> pendiente
 *   5555 5555 5555 4444  -> aprobada (Mastercard)
 *   3782 822463 10005    -> aprobada (American Express, CVV de 4 dígitos)
 */
class SandboxPaymentGateway : PaymentGateway {

    private enum class Scenario { APPROVE, DECLINE, PENDING }

    private val tokens = ConcurrentHashMap<String, Scenario>()
    private val processed = ConcurrentHashMap<String, ChargeResult>()
    private val random = SecureRandom()

    override fun tokenize(card: CardData): TokenizeResult {
        simulateLatency(400)
        val digits = CardValidator.digitsOnly(card.pan)
        val brand = CardValidator.brandOf(digits)
        if (!CardValidator.luhnValid(digits)) {
            return TokenizeResult.Failure("El número de tarjeta no es válido.")
        }
        val scenario = when {
            digits.endsWith("0002") -> Scenario.DECLINE
            digits.endsWith("0259") -> Scenario.PENDING
            else -> Scenario.APPROVE
        }
        val token = "tok_" + randomHex(12)
        tokens[token] = scenario
        return TokenizeResult.Success(token, brand, digits.takeLast(4))
    }

    override fun charge(request: ChargeRequest): ChargeResult {
        processed[request.idempotencyKey]?.let { return it } // mismo intento = mismo resultado, sin doble cobro
        simulateLatency(700)
        val scenario = tokens.remove(request.token) // token de un solo uso
            ?: return ChargeResult.Error("El token de pago no es válido o ya fue usado.")
        val ref = "SBX-" + randomHex(5).uppercase()
        val result = when (scenario) {
            Scenario.APPROVE -> ChargeResult.Approved(ref)
            Scenario.DECLINE -> ChargeResult.Declined(ref, "Tarjeta declinada por el emisor.")
            Scenario.PENDING -> ChargeResult.Pending(ref)
        }
        processed[request.idempotencyKey] = result
        return result
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun simulateLatency(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
