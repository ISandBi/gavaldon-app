package com.gavaldon.go.ui.dispatcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gavaldon.go.AppContainer
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.FragmentDispatchListBinding
import com.gavaldon.go.databinding.FragmentDispatcherHomeBinding
import com.gavaldon.go.databinding.FragmentDispatcherShiftBinding
import com.gavaldon.go.domain.DispatchRules
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.Period
import com.gavaldon.go.domain.PurchaseFilter
import com.gavaldon.go.domain.ShiftOutcome
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.math.BigDecimal

private val Fragment.app: AppContainer
    get() = (requireActivity().application as GavaldonApp).container

/** Panel: contadores de la sucursal y validación de ticket por folio. */
class DispatcherHomeFragment : Fragment() {
    private var _b: FragmentDispatcherHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDispatcherHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnValidate.setOnClickListener { validate() }
        b.etFolio.setOnEditorActionListener { _, _, _ -> validate(); true }
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

    private fun validate() {
        val folio = b.etFolio.text?.toString().orEmpty()
        if (!DispatchRules.isFolio(folio)) {
            b.tilFolio.error = "Folio inválido. Ejemplo: TCK-2026-000001"
            return
        }
        b.tilFolio.error = null
        startActivity(TicketActivity.newIntent(requireContext(), DispatchRules.normalizeFolio(folio)))
        b.etFolio.text?.clear()
    }

    private fun refresh() {
        if (_b == null) return
        val user = app.auth.currentUser() ?: return
        val st = app.dispatchService.stats(user)
        b.tvStation.text = app.stations.station(user.stationId.orEmpty())?.name.orEmpty()
        b.tvGreeting.text = "Despachador: ${user.name}"
        b.tvToDispatch.text = st.toDispatch.toString()
        b.tvWaiting.text = st.waitingPayment.toString()
        b.tvApproved.text = st.approvedToday.toString()
        b.tvDispatched.text = st.dispatchedToday.toString()
        b.tvSales.text = Money.pesos(st.salesToday)
        b.tvLiters.text = Money.liters(st.litersToday)
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}

/** Todas las compras de la sucursal con filtros por estado. */
class DispatcherPurchasesFragment : Fragment() {
    private var _b: FragmentDispatchListBinding? = null
    private val b get() = _b!!
    private var filter = PurchaseFilter.ALL
    private val adapter = PurchaseAdapter { row ->
        startActivity(TicketActivity.newIntent(requireContext(), row.payment.folio))
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDispatchListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.rvItems.layoutManager = LinearLayoutManager(requireContext())
        b.rvItems.adapter = adapter
        b.tvEmpty.setText(R.string.purchases_empty)
        b.chipGroup.setChoices(PurchaseFilter.values().toList(), filter, { it.label }) {
            filter = it
            refresh()
        }
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
        if (_b == null) return
        val user = app.auth.currentUser() ?: return
        val rows = app.dispatchService.purchases(user, filter)
        adapter.submit(rows)
        b.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        b.tvSummary.visibility = View.VISIBLE
        b.tvSummary.text = "${rows.size} compras"
    }

    override fun onDestroyView() {
        b.rvItems.adapter = null
        _b = null
        super.onDestroyView()
    }
}

/** Historial de despachos por periodo (Hoy / Semana / Mes). */
class DispatcherHistoryFragment : Fragment() {
    private var _b: FragmentDispatchListBinding? = null
    private val b get() = _b!!
    private var period = Period.TODAY
    private val adapter = DispatchRecordAdapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDispatchListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.rvItems.layoutManager = LinearLayoutManager(requireContext())
        b.rvItems.adapter = adapter
        b.tvEmpty.setText(R.string.dispatch_history_empty)
        b.chipGroup.setChoices(Period.values().toList(), period, { it.label }) {
            period = it
            refresh()
        }
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
        if (_b == null) return
        val user = app.auth.currentUser() ?: return
        val recs = app.dispatchService.history(user, period)
        adapter.submit(recs)
        b.tvEmpty.visibility = if (recs.isEmpty()) View.VISIBLE else View.GONE
        val liters = recs.fold(BigDecimal.ZERO) { a, r -> a + r.litersDispatched }
        val amount = recs.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
        b.tvSummary.visibility = View.VISIBLE
        b.tvSummary.text = "${recs.size} despachos · ${Money.liters(liters)} · ${Money.pesos(amount)}"
    }

    override fun onDestroyView() {
        b.rvItems.adapter = null
        _b = null
        super.onDestroyView()
    }
}

/** Corte de turno: resumen del turno abierto, cierre y cortes anteriores. */
class DispatcherShiftFragment : Fragment() {
    private var _b: FragmentDispatcherShiftBinding? = null
    private val b get() = _b!!
    private val adapter = ShiftAdapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDispatcherShiftBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.rvItems.layoutManager = LinearLayoutManager(requireContext())
        b.rvItems.adapter = adapter
        b.btnCloseShift.setOnClickListener { confirmClose() }
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

    private fun confirmClose() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shift_confirm_title)
            .setMessage(R.string.shift_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_close_shift) { _, _ -> close() }
            .show()
    }

    private fun close() {
        val user = app.auth.currentUser() ?: return
        val msg = when (val r = app.dispatchService.closeShift(user)) {
            is ShiftOutcome.Closed -> "Corte guardado: ${r.shift.dispatchCount} despachos · ${Money.pesos(r.shift.amountTotal)}"
            is ShiftOutcome.Rejected -> r.message
        }
        Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
        refresh()
    }

    private fun refresh() {
        if (_b == null) return
        val user = app.auth.currentUser() ?: return
        val s = app.dispatchService.shiftSummary(user)
        b.tvSince.text = "Desde ${Fmt.dateTime(s.startEpochMs)}"
        b.tvCount.text = "${s.dispatchCount} despachos"
        b.tvLiters.text = "Litros despachados: ${Money.liters(s.liters)}"
        b.tvAmount.text = Money.pesos(s.amount)
        b.tvDifference.text = Fmt.difference(s.difference)
        if (s.toDispatch > 0) {
            b.tvPendingWarning.text = "${s.toDispatch} tickets pagados siguen sin despachar"
            b.tvPendingWarning.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_schedule, 0, 0, 0)
        } else {
            b.tvPendingWarning.text = "Sin tickets pendientes de despacho"
            b.tvPendingWarning.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
        }
        b.btnCloseShift.isEnabled = s.dispatchCount > 0
        val shifts = app.dispatchService.shifts(user)
        adapter.submit(shifts)
        b.tvEmpty.visibility = if (shifts.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        b.rvItems.adapter = null
        _b = null
        super.onDestroyView()
    }
}
