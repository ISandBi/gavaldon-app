package com.gavaldon.go.data

import com.gavaldon.go.domain.DispatchRecord
import com.gavaldon.go.domain.DispatchRepository
import com.gavaldon.go.domain.FuelType
import com.gavaldon.go.domain.ShiftRecord
import com.gavaldon.go.security.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Despachos y cortes de turno, cifrados en el mismo almacén que los pagos. */
class LocalDispatchRepository(private val store: SecureStore) : DispatchRepository {

    private companion object {
        const val KEY_DISPATCHES = "dispatches"
        const val KEY_SHIFTS = "shifts"
    }

    private val lock = Any()

    override fun forPayment(paymentId: String): DispatchRecord? =
        allDispatches().firstOrNull { it.paymentId == paymentId }

    override fun forStation(stationId: String): List<DispatchRecord> =
        allDispatches().filter { it.stationId == stationId }

    override fun add(record: DispatchRecord): Boolean = synchronized(lock) {
        val array = readArray(KEY_DISPATCHES)
        for (i in 0 until array.length()) {
            if (array.optJSONObject(i)?.optString("paymentId") == record.paymentId) return@synchronized false
        }
        array.put(
            JSONObject()
                .put("id", record.id).put("paymentId", record.paymentId).put("folio", record.folio)
                .put("stationId", record.stationId).put("dispatcherId", record.dispatcherId)
                .put("dispatcherName", record.dispatcherName).put("fuel", record.fuel.name)
                .put("purchased", record.litersPurchased.toPlainString())
                .put("dispatched", record.litersDispatched.toPlainString())
                .put("amount", record.amount.toPlainString())
                .put("at", record.dispatchedAtEpochMs)
        )
        store.putString(KEY_DISPATCHES, array.toString())
        true
    }

    override fun lastShiftEnd(stationId: String): Long? =
        shifts(stationId).maxOfOrNull { it.endEpochMs }

    override fun addShift(shift: ShiftRecord) {
        synchronized(lock) {
            val array = readArray(KEY_SHIFTS)
            array.put(
                JSONObject()
                    .put("id", shift.id).put("stationId", shift.stationId)
                    .put("dispatcherId", shift.dispatcherId).put("dispatcherName", shift.dispatcherName)
                    .put("start", shift.startEpochMs).put("end", shift.endEpochMs)
                    .put("count", shift.dispatchCount)
                    .put("liters", shift.litersDispatched.toPlainString())
                    .put("amount", shift.amountTotal.toPlainString())
                    .put("difference", shift.differenceLiters.toPlainString())
            )
            store.putString(KEY_SHIFTS, array.toString())
        }
    }

    override fun shifts(stationId: String): List<ShiftRecord> = synchronized(lock) {
        val array = readArray(KEY_SHIFTS)
        val out = ArrayList<ShiftRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optString("stationId") != stationId) continue
            out.add(
                ShiftRecord(
                    id = o.optString("id"), stationId = stationId,
                    dispatcherId = o.optString("dispatcherId"), dispatcherName = o.optString("dispatcherName"),
                    startEpochMs = o.optLong("start"), endEpochMs = o.optLong("end"),
                    dispatchCount = o.optInt("count"),
                    litersDispatched = BigDecimal(o.optString("liters", "0")),
                    amountTotal = BigDecimal(o.optString("amount", "0")),
                    differenceLiters = BigDecimal(o.optString("difference", "0"))
                )
            )
        }
        out
    }

    private fun allDispatches(): List<DispatchRecord> = synchronized(lock) {
        val array = readArray(KEY_DISPATCHES)
        val out = ArrayList<DispatchRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val fuel = runCatching { FuelType.valueOf(o.optString("fuel")) }.getOrNull() ?: continue
            out.add(
                DispatchRecord(
                    id = o.optString("id"), paymentId = o.optString("paymentId"), folio = o.optString("folio"),
                    stationId = o.optString("stationId"), dispatcherId = o.optString("dispatcherId"),
                    dispatcherName = o.optString("dispatcherName"), fuel = fuel,
                    litersPurchased = BigDecimal(o.optString("purchased", "0")),
                    litersDispatched = BigDecimal(o.optString("dispatched", "0")),
                    amount = BigDecimal(o.optString("amount", "0")),
                    dispatchedAtEpochMs = o.optLong("at")
                )
            )
        }
        out
    }

    private fun readArray(key: String): JSONArray =
        try {
            JSONArray(store.getString(key) ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
}
