package com.gavaldon.go.data

import com.gavaldon.go.domain.Promotion
import com.gavaldon.go.domain.PromotionRepository
import java.time.LocalDate

/**
 * Promociones de EJEMPLO para demostrar RF-19/RF-22. Las reales las administrará GAVALDON (RF-20) vía API.
 * La tercera está vencida a propósito: no debe aparecer en la lista.
 */
class LocalPromotionRepository : PromotionRepository {

    private val all = listOf(
        Promotion(
            "p1", "Promoción de ejemplo: Premium",
            "Texto de muestra. Aquí irá la descripción administrada por GAVALDON.",
            "Beneficio de ejemplo",
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), null
        ),
        Promotion(
            "p2", "Promoción de ejemplo: estación Cuauhtemoc",
            "Texto de muestra para una promoción asociada a una sola gasolinera.",
            "Beneficio de ejemplo",
            LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 30), "cuauhtemoc"
        ),
        Promotion(
            "p3", "Promoción vencida (no se muestra)",
            "Debe filtrarse por RF-22.",
            "—",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null
        )
    )

    override fun active(today: LocalDate): List<Promotion> =
        all.filter { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
}
