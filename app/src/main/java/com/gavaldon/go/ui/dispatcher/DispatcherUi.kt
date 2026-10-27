package com.gavaldon.go.ui.dispatcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ItemDispatchPurchaseBinding
import com.gavaldon.go.databinding.ItemDispatchRecordBinding
import com.gavaldon.go.databinding.ItemShiftBinding
import com.gavaldon.go.domain.DispatchRecord
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PaymentStatus
import com.gavaldon.go.domain.PurchaseRow
import com.gavaldon.go.domain.ShiftRecord
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTime)

    /** Diferencia con texto (no solo color): positivo = se entregó menos de lo pagado. */
    fun difference(diff: BigDecimal): String = when {
        diff.signum() == 0 -> "Sin diferencia (despacho completo)"
        diff.signum() > 0 -> "Faltaron ${Money.liters(diff)} por entregar"
        else -> "Sobraron ${Money.liters(diff.negate())}"
    }
}

/** Chips de selección única (filtros). La selección activa lleva palomita, no solo color. */
fun <T> ChipGroup.setChoices(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    items.forEach { item ->
        val chip = inflater.inflate(R.layout.view_chip_choice, this, false) as Chip
        chip.id = View.generateViewId()
        chip.text = label(item)
        chip.isChecked = item == selected
        chip.setOnClickListener { onPick(item) }
        addView(chip)
    }
}

class PurchaseAdapter(private val onClick: (PurchaseRow) -> Unit) :
    RecyclerView.Adapter<PurchaseAdapter.Holder>() {
    private var items: List<PurchaseRow> = emptyList()

    fun submit(newItems: List<PurchaseRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDispatchPurchaseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemDispatchPurchaseBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: PurchaseRow) {
            val p = row.payment
            b.tvFolio.text = p.folio
            val (label, icon) = when {
                row.dispatch != null -> "Despachada" to R.drawable.ic_check_circle
                p.status == PaymentStatus.APPROVED -> "Por despachar" to R.drawable.ic_pump
                p.status == PaymentStatus.PENDING -> "Pago pendiente" to R.drawable.ic_schedule
                else -> "Pago rechazado" to R.drawable.ic_cancel
            }
            b.tvStatus.text = label
            b.tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            b.tvCustomer.text = row.customerName
            b.tvDetail.text = "${p.fuel.label} · ${Money.liters(p.liters)}"
            b.tvAmount.text = Money.pesos(p.amount)
            b.tvDate.text = Fmt.dateTime(p.createdAtEpochMs)
            b.root.setOnClickListener { onClick(row) }
        }
    }
}

class DispatchRecordAdapter : RecyclerView.Adapter<DispatchRecordAdapter.Holder>() {
    private var items: List<DispatchRecord> = emptyList()

    fun submit(newItems: List<DispatchRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemDispatchRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemDispatchRecordBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: DispatchRecord) {
            b.tvFolio.text = r.folio
            b.tvDetail.text = "${r.fuel.label} · pagado ${Money.liters(r.litersPurchased)} · " +
                "despachado ${Money.liters(r.litersDispatched)} · ${Money.pesos(r.amount)}"
            b.tvDifference.text = Fmt.difference(r.difference)
            val icon = if (r.difference.signum() == 0) R.drawable.ic_check_circle else R.drawable.ic_schedule
            b.tvDifference.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            b.tvMeta.text = "${Fmt.dateTime(r.dispatchedAtEpochMs)} · ${r.dispatcherName}"
        }
    }
}

class ShiftAdapter : RecyclerView.Adapter<ShiftAdapter.Holder>() {
    private var items: List<ShiftRecord> = emptyList()

    fun submit(newItems: List<ShiftRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemShiftBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemShiftBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: ShiftRecord) {
            b.tvPeriod.text = "${Fmt.dateTime(s.startEpochMs)} → ${Fmt.dateTime(s.endEpochMs)}"
            b.tvDetail.text = "${s.dispatchCount} despachos · ${Money.liters(s.litersDispatched)} · " +
                Money.pesos(s.amountTotal)
            b.tvMeta.text = "${Fmt.difference(s.differenceLiters)} · ${s.dispatcherName}"
        }
    }
}
