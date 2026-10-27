package com.gavaldon.go.domain

import java.math.BigDecimal
import java.time.LocalDate

/*
 * Contratos (principio de inversión de dependencias, SOLID).
 * La UI depende de estas interfaces. Hoy las implementa el paquete `data` con almacenamiento local cifrado;
 * al existir la API REST de Gavaldon (MySQL detrás, como pide la documentación) solo se cambian las
 * implementaciones en AppContainer; ninguna pantalla se modifica.
 */

interface StationRepository {
    fun stations(): List<Station>
    fun station(id: String): Station?
    fun prices(stationId: String): List<FuelPrice>
}

interface PromotionRepository {
    /** RF-22: solo promociones dentro de su periodo de vigencia. */
    fun active(today: LocalDate): List<Promotion>
}

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

interface AuthRepository {
    fun register(name: String, email: String, phone: String, password: CharArray): AuthResult
    /** Alta de despachador: una sola persona por sucursal. */
    fun registerDispatcher(name: String, email: String, phone: String, password: CharArray, stationId: String): AuthResult
    /** [role] es el perfil que intenta entrar; una cuenta de otro perfil falla con el mismo mensaje genérico. */
    fun login(email: String, password: CharArray, role: Role): AuthResult
    fun isStationTaken(stationId: String): Boolean
    fun displayName(userId: String): String?
    fun currentUser(): User?
    fun isSessionValid(): Boolean
    fun touchSession()
    fun logout()
}

interface PaymentRepository {
    fun add(record: PaymentRecord)
    fun history(userId: String): List<PaymentRecord>
    fun forStation(stationId: String): List<PaymentRecord>
    fun byFolio(folio: String): PaymentRecord?
    fun nextFolio(): String
}

/** Datos de tarjeta en memoria el menor tiempo posible. toString() nunca revela datos (PCI DSS 3.3 / M9). */
class CardData(
    val holder: String,
    val pan: String,
    val expMonth: Int,
    val expYear: Int,
    val cvv: String
) {
    override fun toString(): String = "CardData(***)"
}

sealed class TokenizeResult {
    data class Success(val token: String, val brand: CardBrand, val last4: String) : TokenizeResult()
    data class Failure(val message: String) : TokenizeResult()
}

data class ChargeRequest(
    val token: String,
    val amount: BigDecimal,
    val currency: String,
    /** Evita doble cobro si el usuario toca dos veces o se reintenta la petición. */
    val idempotencyKey: String,
    val description: String
)

sealed class ChargeResult {
    data class Approved(val reference: String) : ChargeResult()
    data class Declined(val reference: String, val reason: String) : ChargeResult()
    data class Pending(val reference: String) : ChargeResult()
    data class Error(val message: String) : ChargeResult()
}

/**
 * Frontera con el procesador de pagos autorizado por GAVALDON (RF-07/RF-08).
 * En producción: el SDK del proveedor tokeniza la tarjeta (el PAN no pasa por código propio) y el backend
 * hace el cobro con el token. Ver docs/SEGURIDAD_OWASP_PCI.md.
 */
interface PaymentGateway {
    fun tokenize(card: CardData): TokenizeResult
    fun charge(request: ChargeRequest): ChargeResult
}

interface DispatchRepository {
    fun forPayment(paymentId: String): DispatchRecord?
    fun forStation(stationId: String): List<DispatchRecord>
    /** @return false si ese pago ya fue despachado (impide doble despacho). */
    fun add(record: DispatchRecord): Boolean
    fun lastShiftEnd(stationId: String): Long?
    fun addShift(shift: ShiftRecord)
    fun shifts(stationId: String): List<ShiftRecord>
}
