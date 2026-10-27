package com.gavaldon.go.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class PurchaseFilter(val label: String) {
    ALL("Todas"), TO_DISPATCH("Por despachar"), DISPATCHED("Despachadas"),
    WAITING("Pago pendiente"), REJECTED("Rechazadas")
}

enum class Period(val label: String) { TODAY("Hoy"), WEEK("Semana"), MONTH("Mes") }

data class PurchaseRow(val payment: PaymentRecord, val dispatch: DispatchRecord?, val customerName: String)

data class StationStats(
    val approvedToday: Int,
    val dispatchedToday: Int,
    val toDispatch: Int,
    val waitingPayment: Int,
    val salesToday: BigDecimal,
    val litersToday: BigDecimal
)

data class ShiftSummary(
    val startEpochMs: Long,
    val dispatchCount: Int,
    val liters: BigDecimal,
    val amount: BigDecimal,
    val difference: BigDecimal,
    val toDispatch: Int
)

sealed class TicketCheck {
    data class Ready(val payment: PaymentRecord) : TicketCheck()
    data class OtherStation(val payment: PaymentRecord) : TicketCheck()
    data class NotApproved(val payment: PaymentRecord) : TicketCheck()
    data class AlreadyDispatched(val payment: PaymentRecord, val record: DispatchRecord) : TicketCheck()
    data object NotFound : TicketCheck()
    data object InvalidFormat : TicketCheck()
    data object Unauthorized : TicketCheck()
}

sealed class DispatchOutcome {
    data class Done(val record: DispatchRecord) : DispatchOutcome()
    data class Rejected(val message: String) : DispatchOutcome()
}

sealed class ShiftOutcome {
    data class Closed(val shift: ShiftRecord) : ShiftOutcome()
    data class Rejected(val message: String) : ShiftOutcome()
}

/** Reglas puras de validación (sin Android; probadas en DomainTests). */
object DispatchRules {
    private val FOLIO = Regex("^TCK-\\d{4}-\\d{6}$")
    private val LITERS = Regex("^\\d{1,4}([.,]\\d{1,2})?$")

    fun normalizeFolio(raw: String): String = raw.trim().uppercase()
    fun isFolio(raw: String): Boolean = FOLIO.matches(normalizeFolio(raw))

    fun parseLiters(raw: String): BigDecimal? {
        val t = raw.trim()
        if (!LITERS.matches(t)) return null
        return BigDecimal(t.replace(',', '.'))
    }

    /** null = válido. Nunca se despacha más de lo pagado ni cero. */
    fun validateLiters(raw: String, purchased: BigDecimal): String? {
        val v = parseLiters(raw) ?: return "Escribe los litros con máximo 2 decimales."
        if (v.signum() <= 0) return "Los litros deben ser mayores a 0."
        if (v > purchased) return "No puedes despachar más de lo pagado (${purchased.toPlainString()} L)."
        return null
    }
}

/**
 * Casos de uso del despachador. TODA regla de autorización vive aquí (no en la UI): un despachador solo
 * ve y opera tickets de SU sucursal (OWASP M3 / A01 broken access control). En producción esta clase
 * se ejecuta en el servidor.
 */
class DispatchService(
    private val auth: AuthRepository,
    private val payments: PaymentRepository,
    private val dispatches: DispatchRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private fun stationOf(user: User): String? =
        if (user.role == Role.DISPATCHER) user.stationId else null

    private fun startOfToday(): Long =
        Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun startOf(period: Period): Long {
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val first = when (period) {
            Period.TODAY -> today
            Period.WEEK -> today.minusDays(6)
            Period.MONTH -> today.withDayOfMonth(1)
        }
        return first.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun check(user: User, rawFolio: String): TicketCheck {
        val station = stationOf(user) ?: return TicketCheck.Unauthorized
        if (!DispatchRules.isFolio(rawFolio)) return TicketCheck.InvalidFormat
        val p = payments.byFolio(DispatchRules.normalizeFolio(rawFolio)) ?: return TicketCheck.NotFound
        if (p.stationId != station) return TicketCheck.OtherStation(p)
        if (p.status != PaymentStatus.APPROVED) return TicketCheck.NotApproved(p)
        dispatches.forPayment(p.id)?.let { return TicketCheck.AlreadyDispatched(p, it) }
        return TicketCheck.Ready(p)
    }

    fun confirm(user: User, rawFolio: String, litersRaw: String): DispatchOutcome {
        val ready = check(user, rawFolio) as? TicketCheck.Ready
            ?: return DispatchOutcome.Rejected("Este ticket no se puede despachar en tu sucursal.")
        val p = ready.payment
        DispatchRules.validateLiters(litersRaw, p.liters)?.let { return DispatchOutcome.Rejected(it) }
        val record = DispatchRecord(
            id = UUID.randomUUID().toString(), paymentId = p.id, folio = p.folio, stationId = p.stationId,
            dispatcherId = user.id, dispatcherName = user.name, fuel = p.fuel,
            litersPurchased = p.liters, litersDispatched = DispatchRules.parseLiters(litersRaw)!!,
            amount = p.amount, dispatchedAtEpochMs = clock()
        )
        return if (dispatches.add(record)) DispatchOutcome.Done(record)
        else DispatchOutcome.Rejected("Este ticket ya fue despachado.")
    }

    fun purchases(user: User, filter: PurchaseFilter): List<PurchaseRow> {
        val station = stationOf(user) ?: return emptyList()
        val done = dispatches.forStation(station).associateBy { it.paymentId }
        return payments.forStation(station)
            .map { PurchaseRow(it, done[it.id], auth.displayName(it.userId) ?: "Cliente") }
            .filter {
                when (filter) {
                    PurchaseFilter.ALL -> true
                    PurchaseFilter.TO_DISPATCH -> it.payment.status == PaymentStatus.APPROVED && it.dispatch == null
                    PurchaseFilter.DISPATCHED -> it.dispatch != null
                    PurchaseFilter.WAITING -> it.payment.status == PaymentStatus.PENDING
                    PurchaseFilter.REJECTED -> it.payment.status == PaymentStatus.REJECTED
                }
            }
    }

    fun history(user: User, period: Period): List<DispatchRecord> {
        val station = stationOf(user) ?: return emptyList()
        val from = startOf(period)
        return dispatches.forStation(station)
            .filter { it.dispatchedAtEpochMs >= from }
            .sortedByDescending { it.dispatchedAtEpochMs }
    }

    fun stats(user: User): StationStats {
        val station = stationOf(user)
            ?: return StationStats(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO)
        val today = startOfToday()
        val all = payments.forStation(station)
        val done = dispatches.forStation(station)
        val doneIds = done.map { it.paymentId }.toSet()
        val approvedToday = all.filter { it.status == PaymentStatus.APPROVED && it.createdAtEpochMs >= today }
        val dispatchedToday = done.filter { it.dispatchedAtEpochMs >= today }
        return StationStats(
            approvedToday = approvedToday.size,
            dispatchedToday = dispatchedToday.size,
            toDispatch = all.count { it.status == PaymentStatus.APPROVED && it.id !in doneIds },
            waitingPayment = all.count { it.status == PaymentStatus.PENDING },
            salesToday = approvedToday.fold(BigDecimal.ZERO) { a, p -> a + p.amount },
            litersToday = dispatchedToday.fold(BigDecimal.ZERO) { a, d -> a + d.litersDispatched }
        )
    }

    private fun currentShiftRecords(station: String): List<DispatchRecord> {
        val from = dispatches.lastShiftEnd(station) ?: 0L
        return dispatches.forStation(station).filter { it.dispatchedAtEpochMs > from }
    }

    fun shiftSummary(user: User): ShiftSummary {
        val station = stationOf(user)
            ?: return ShiftSummary(clock(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0)
        val recs = currentShiftRecords(station)
        return ShiftSummary(
            startEpochMs = dispatches.lastShiftEnd(station) ?: recs.minOfOrNull { it.dispatchedAtEpochMs } ?: clock(),
            dispatchCount = recs.size,
            liters = recs.fold(BigDecimal.ZERO) { a, r -> a + r.litersDispatched },
            amount = recs.fold(BigDecimal.ZERO) { a, r -> a + r.amount },
            difference = recs.fold(BigDecimal.ZERO) { a, r -> a + r.difference },
            toDispatch = stats(user).toDispatch
        )
    }

    fun closeShift(user: User): ShiftOutcome {
        val station = stationOf(user) ?: return ShiftOutcome.Rejected("Acceso no autorizado.")
        val s = shiftSummary(user)
        if (s.dispatchCount == 0) return ShiftOutcome.Rejected("No hay despachos en este turno.")
        val shift = ShiftRecord(
            id = UUID.randomUUID().toString(), stationId = station, dispatcherId = user.id,
            dispatcherName = user.name, startEpochMs = s.startEpochMs, endEpochMs = clock(),
            dispatchCount = s.dispatchCount, litersDispatched = s.liters, amountTotal = s.amount,
            differenceLiters = s.difference
        )
        dispatches.addShift(shift)
        return ShiftOutcome.Closed(shift)
    }

    fun shifts(user: User): List<ShiftRecord> {
        val station = stationOf(user) ?: return emptyList()
        return dispatches.shifts(station).sortedByDescending { it.endEpochMs }
    }
}
