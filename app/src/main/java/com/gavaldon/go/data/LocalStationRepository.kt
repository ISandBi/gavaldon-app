package com.gavaldon.go.data

import com.gavaldon.go.domain.FuelPrice
import com.gavaldon.go.domain.FuelType
import com.gavaldon.go.domain.Station
import com.gavaldon.go.domain.StationRepository
import java.math.BigDecimal

/**
 * Catálogo de las 10 estaciones con las coordenadas entregadas por el cliente (verificadas: DMS = decimal).
 * Sustituir por una implementación que llame a la API REST cuando exista (RF-04, precios en tiempo real).
 */
class LocalStationRepository : StationRepository {

    private val stations = listOf(
        Station("cuauhtemoc", "GAVALDON CUAHTEMOC", 19.4315751, -99.1596397, "19°25'53.67\"N 99°09'34.70\"W"),
        Station("buenavista", "GAVALDON BUENAVISTA", 19.4466273, -99.1535030, "19°26'47.85\"N 99°09'12.61\"W"),
        Station("san_cosme", "GAVALDON SAN COSME", 19.4423814, -99.1632747, "19°26'32.57\"N 99°09'47.78\"W"),
        Station("centro", "GAVALDON CENTRO", 19.4271093, -99.1392078, "19°25'37.59\"N 99°08'21.14\"W"),
        Station("garibaldi", "GAVALDON GARIBALDI", 19.4404260, -99.1401508, "19°26'25.53\"N 99°08'24.54\"W"),
        Station("reforma", "GAVALDON REFORMA", 19.4384596, -99.1461508, "19°26'18.45\"N 99°08'46.14\"W"),
        Station("circuito", "GAVALDON CIRCUITO", 19.4385057, -99.1679470, "19°26'18.62\"N 99°10'04.60\"W"),
        Station("guerrero", "GAVALDON GUERRERO", 19.4455684, -99.1470796, "19°26'44.04\"N 99°08'49.48\"W"),
        Station("tlatelolco", "GAVALDON TLATELOLCO", 19.4557841, -99.1405839, "19°27'20.82\"N 99°08'26.10\"W"),
        Station("la_villa", "GAVALDON LA VILLA", 19.4850501, -99.1189796, "19°29'06.18\"N 99°07'08.32\"W")
    )

    // Precios del wireframe. Iguales en todas las estaciones hasta contar con la API.
    private val prices = listOf(
        FuelPrice(FuelType.PREMIUM, BigDecimal("29.80")),
        FuelPrice(FuelType.MAGNA, BigDecimal("26.80")),
        FuelPrice(FuelType.DIESEL, BigDecimal("30.80"))
    )

    override fun stations(): List<Station> = stations

    override fun station(id: String): Station? = stations.firstOrNull { it.id == id }

    override fun prices(stationId: String): List<FuelPrice> = prices
}
