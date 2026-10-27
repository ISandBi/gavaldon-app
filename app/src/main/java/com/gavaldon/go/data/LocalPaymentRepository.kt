package com.gavaldon.go.data

import com.gavaldon.go.domain.FuelType
import com.gavaldon.go.domain.PaymentRecord
import com.gavaldon.go.domain.PaymentRepository
import com.gavaldon.go.domain.PaymentStatus
import com.gavaldon.go.security.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** RF-11 / RF-12. Registro cifrado de pagos. Solo datos no sensibles (ver [PaymentRecord]). */
class LocalPaymentRepository(private val store: SecureStore) : PaymentRepository {

    private companion object {
        const val KEY_PAYMENTS = "payments"
        const val KEY_FOLIO = "folio_counter"
    }

    private val lock = Any()

    override fun add(record: PaymentRecord) {
        synchronized(lock) {
            val array = readArray()
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("folio", record.folio)
                    .put("userId", record.userId)
                    .put("stationId", record.stationId)
                    .put("stationName", record.stationName)
                    .put("fuel", record.fuel.name)
                    .put("liters", record.liters.toPlainString())
                    .put("amount", record.amount.toPlainString())
                    .put("status", record.status.name)
                    .put("providerRef", record.providerRef)
                    .put("createdAt", record.createdAtEpochMs)
                    .put("cardBrand", record.cardBrand)
                    .put("cardLast4", record.cardLast4)
            )
            store.putString(KEY_PAYMENTS, array.toString())
        }
    }

    override fun history(userId: String): List<PaymentRecord> =
        all().filter { it.userId == userId }.sortedByDescending { it.createdAtEpochMs }

    override fun forStation(stationId: String): List<PaymentRecord> =
        all().filter { it.stationId == stationId }.sortedByDescending { it.createdAtEpochMs }

    override fun byFolio(folio: String): PaymentRecord? = all().firstOrNull { it.folio == folio }

    private fun all(): List<PaymentRecord> = synchronized(lock) {
        val array = readArray()
        val out = ArrayList<PaymentRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val fuel = runCatching { FuelType.valueOf(o.optString("fuel")) }.getOrNull() ?: continue
            val status = runCatching { PaymentStatus.valueOf(o.optString("status")) }.getOrNull() ?: continue
            out.add(
                PaymentRecord(
                    id = o.optString("id"),
                    folio = o.optString("folio"),
                    userId = o.optString("userId"),
                    stationId = o.optString("stationId"),
                    stationName = o.optString("stationName"),
                    fuel = fuel,
                    liters = BigDecimal(o.optString("liters", "0")),
                    amount = BigDecimal(o.optString("amount", "0")),
                    status = status,
                    providerRef = o.optString("providerRef"),
                    createdAtEpochMs = o.optLong("createdAt"),
                    cardBrand = o.optString("cardBrand"),
                    cardLast4 = o.optString("cardLast4")
                )
            )
        }
        out
    }

    override fun nextFolio(): String = synchronized(lock) {
        val next = store.getLong(KEY_FOLIO, 0L) + 1
        store.putLong(KEY_FOLIO, next)
        String.format(Locale.US, "TCK-%d-%06d", LocalDate.now().year, next)
    }

    private fun readArray(): JSONArray =
        try {
            JSONArray(store.getString(KEY_PAYMENTS) ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
}
