package com.gavaldon.go.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.FragmentListBinding
import com.gavaldon.go.databinding.ItemPaymentBinding
import com.gavaldon.go.domain.CardValidator
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PaymentRecord
import com.gavaldon.go.domain.PaymentStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RF-12: historial de pagos del usuario (fecha, monto, gasolinera, estado, referencia). */
class HistoryFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = PaymentAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter
        binding.tvEmpty.setText(R.string.history_empty)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    private fun refresh() {
        if (_binding == null) return
        val container = (requireActivity().application as GavaldonApp).container
        val user = container.auth.currentUser() ?: return
        val items = container.payments.history(user.id)
        adapter.submit(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        binding.rvItems.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private class PaymentAdapter : RecyclerView.Adapter<PaymentAdapter.Holder>() {
    private var items: List<PaymentRecord> = emptyList()
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "MX"))

    fun submit(newItems: List<PaymentRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemPaymentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: PaymentRecord) {
            b.tvDate.text = Instant.ofEpochMilli(p.createdAtEpochMs)
                .atZone(ZoneId.systemDefault()).format(dateFormat)
            b.tvStatus.text = p.status.label
            val icon = when (p.status) {
                PaymentStatus.APPROVED -> R.drawable.ic_check_circle
                PaymentStatus.REJECTED -> R.drawable.ic_cancel
                PaymentStatus.PENDING -> R.drawable.ic_schedule
            }
            b.tvStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            b.tvStation.text = p.stationName
            b.tvDetail.text = "${p.fuel.label} · ${Money.liters(p.liters)}"
            b.tvAmount.text = Money.pesos(p.amount)
            b.tvReference.text = "${p.folio} · ${p.providerRef}"
            b.tvCard.text = "${p.cardBrand} ${CardValidator.maskLast4(p.cardLast4)}"
        }
    }
}
