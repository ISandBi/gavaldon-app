package com.gavaldon.go.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distancia en metros entre dos puntos (fórmula de Haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    fun formatDistance(meters: Double): String =
        if (meters < 1000) String.format(Locale.US, "%d m", Math.round(meters))
        else String.format(Locale.US, "%.1f km", meters / 1000.0)

    fun nearest(stations: List<Station>, lat: Double, lon: Double): Pair<Station, Double>? =
        stations
            .map { it to distanceMeters(lat, lon, it.latitude, it.longitude) }
            .minByOrNull { it.second }
}

object Money {
    private val MX = Locale("es", "MX")

    fun pesos(amount: BigDecimal): String = NumberFormat.getCurrencyInstance(MX).format(amount)

    fun price(amount: BigDecimal): String = pesos(amount.setScale(2, RoundingMode.HALF_UP))

    /** Litros = monto / precio, truncado a 2 decimales (nunca se promete más combustible del pagado). */
    fun litersFor(amount: BigDecimal, pricePerLiter: BigDecimal): BigDecimal =
        amount.divide(pricePerLiter, 2, RoundingMode.DOWN)

    fun liters(liters: BigDecimal): String = String.format(Locale.US, "%.2f L", liters)
}
