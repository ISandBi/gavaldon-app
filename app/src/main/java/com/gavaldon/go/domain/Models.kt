package com.gavaldon.go.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class FuelType(val label: String) {
    PREMIUM("Premium"),
    MAGNA("Magna"),
    DIESEL("Diésel")
}

data class Station(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Ubicación en grados/minutos/segundos tal como la entrega el cliente. */
    val dmsLocation: String
)

data class FuelPrice(val fuel: FuelType, val pricePerLiter: BigDecimal)

enum class Role { CUSTOMER, DISPATCHER }

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: Role = Role.CUSTOMER,
    /** Sucursal asignada; solo aplica al despachador. */
    val stationId: String? = null
)

enum class PaymentStatus(val label: String) {
    APPROVED("Pago aprobado"),
    REJECTED("Pago rechazado"),
    PENDING("Pago pendiente")
}

/**
 * RF-11. Solo guarda datos NO sensibles: marca y últimos 4 dígitos (truncamiento permitido por PCI DSS 3.4).
 * Nunca se guarda el PAN completo, el CVV ni la fecha de vencimiento.
 */
data class PaymentRecord(
    val id: String,
    val folio: String,
    val userId: String,
    val stationId: String,
    val stationName: String,
    val fuel: FuelType,
    val liters: BigDecimal,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val providerRef: String,
    val createdAtEpochMs: Long,
    val cardBrand: String,
    val cardLast4: String
)

data class Promotion(
    val id: String,
    val title: String,
    val description: String,
    val benefit: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** null = aplica en todas las estaciones. */
    val stationId: String?
)

/** Despacho confirmado por un despachador. Un pago aprobado solo puede despacharse una vez. */
data class DispatchRecord(
    val id: String,
    val paymentId: String,
    val folio: String,
    val stationId: String,
    val dispatcherId: String,
    val dispatcherName: String,
    val fuel: FuelType,
    val litersPurchased: BigDecimal,
    val litersDispatched: BigDecimal,
    val amount: BigDecimal,
    val dispatchedAtEpochMs: Long
) {
    /** Positivo = se despachó menos de lo pagado. */
    val difference: BigDecimal get() = litersPurchased.subtract(litersDispatched)
}

/** Corte de turno: resumen inmutable de los despachos entre dos cortes. */
data class ShiftRecord(
    val id: String,
    val stationId: String,
    val dispatcherId: String,
    val dispatcherName: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val dispatchCount: Int,
    val litersDispatched: BigDecimal,
    val amountTotal: BigDecimal,
    val differenceLiters: BigDecimal
)
