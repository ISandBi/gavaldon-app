package com.gavaldon.go.ui.dispatcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityTicketBinding
import com.gavaldon.go.domain.DispatchOutcome
import com.gavaldon.go.domain.DispatchRecord
import com.gavaldon.go.domain.DispatchRules
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PaymentRecord
import com.gavaldon.go.domain.PaymentStatus
import com.gavaldon.go.domain.Role
import com.gavaldon.go.domain.TicketCheck
import com.gavaldon.go.ui.base.SecureActivity
import com.gavaldon.go.util.afterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal

/**
 * Valida un ticket y registra el despacho. La autorización (¿es de mi sucursal? ¿está aprobado?
 * ¿ya se despachó?) la decide DispatchService, no esta pantalla.
 */
class TicketActivity : SecureActivity() {

    companion object {
        private const val EXTRA_FOLIO = "folio"
        fun newIntent(context: Context, folio: String): Intent =
            Intent(context, TicketActivity::class.java).putExtra(EXTRA_FOLIO, folio)
    }

    override val requiredRole: Role = Role.DISPATCHER

    private lateinit var binding: ActivityTicketBinding
    private var folio = ""
    private var payment: PaymentRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        folio = intent.getStringExtra(EXTRA_FOLIO).orEmpty()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { askConfirm() }
        binding.etLiters.afterTextChanged { updateDifference() }
        load()
    }

    private fun load() {
        val user = container.auth.currentUser()
        if (user == null) {
            expireSession()
            return
        }
        when (val c = container.dispatchService.check(user, folio)) {
            is TicketCheck.Ready -> showReady(c.payment)
            is TicketCheck.OtherStation -> {
                val name = container.stations.station(c.payment.stationId)?.name ?: "otra sucursal"
                // No se muestra ningún dato de la compra: solo que no corresponde a esta sucursal.
                showMessage(R.drawable.ic_cancel, R.string.ticket_other_station,
                    "Este ticket pertenece a $name. No puedes despacharlo en tu sucursal.")
            }
            is TicketCheck.NotApproved -> {
                val pending = c.payment.status == PaymentStatus.PENDING
                showMessage(
                    if (pending) R.drawable.ic_schedule else R.drawable.ic_cancel,
                    R.string.ticket_not_approved,
                    if (pending) "El pago sigue pendiente. No entregues combustible hasta que se apruebe."
                    else "El pago fue rechazado. No se puede despachar.",
                    c.payment
                )
            }
            is TicketCheck.AlreadyDispatched -> showMessage(
                R.drawable.ic_check_circle, R.string.ticket_already,
                "Despachado el ${Fmt.dateTime(c.record.dispatchedAtEpochMs)} por ${c.record.dispatcherName}.",
                c.payment, c.record
            )
            TicketCheck.NotFound -> showMessage(R.drawable.ic_cancel, R.string.ticket_not_found,
                "No existe un ticket con el folio $folio.")
            TicketCheck.InvalidFormat -> showMessage(R.drawable.ic_cancel, R.string.ticket_invalid,
                "Usa el formato TCK-AAAA-######.")
            TicketCheck.Unauthorized -> showMessage(R.drawable.ic_cancel, R.string.ticket_unauthorized,
                getString(R.string.dispatcher_wrong_role))
        }
    }

    private fun fillDetail(p: PaymentRecord) {
        binding.cardDetail.visibility = View.VISIBLE
        binding.tvFolio.text = p.folio
        binding.tvCustomer.text = container.auth.displayName(p.userId) ?: "Cliente"
        binding.tvDetail.text = "${p.fuel.label} · ${Money.liters(p.liters)}"
        binding.tvAmount.text = Money.pesos(p.amount)
    }

    private fun showMessage(icon: Int, title: Int, message: String, p: PaymentRecord? = null, rec: DispatchRecord? = null) {
        binding.imgStatus.setImageResource(icon)
        binding.tvStatus.setText(title)
        binding.tvMessage.text = message
        binding.tilLiters.visibility = View.GONE
        binding.tvDifference.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.cardDetail.visibility = View.GONE
        binding.tvResult.visibility = View.GONE
        if (p != null) fillDetail(p)
        if (rec != null) showResult(rec)
    }

    private fun showResult(rec: DispatchRecord) {
        binding.tvResult.visibility = View.VISIBLE
        binding.tvResult.text = "Despachado: ${Money.liters(rec.litersDispatched)}\n${Fmt.difference(rec.difference)}"
    }

    private fun showReady(p: PaymentRecord) {
        payment = p
        binding.imgStatus.setImageResource(R.drawable.ic_check_circle)
        binding.tvStatus.setText(R.string.ticket_ready)
        binding.tvMessage.text = "Surte el combustible y registra los litros realmente despachados."
        fillDetail(p)
        binding.tvResult.visibility = View.GONE
        binding.tilLiters.visibility = View.VISIBLE
        binding.tvDifference.visibility = View.VISIBLE
        binding.btnConfirm.visibility = View.VISIBLE
        binding.etLiters.setText(p.liters.toPlainString())
        binding.etLiters.setSelection(binding.etLiters.text?.length ?: 0)
    }

    private fun updateDifference() {
        val p = payment ?: return
        val raw = binding.etLiters.text?.toString().orEmpty()
        val error = DispatchRules.validateLiters(raw, p.liters)
        binding.tilLiters.error = if (raw.isEmpty()) null else error
        val v = DispatchRules.parseLiters(raw)
        binding.tvDifference.text = if (v == null || error != null) "" else Fmt.difference(p.liters.subtract(v))
    }

    private fun askConfirm() {
        val p = payment ?: return
        val raw = binding.etLiters.text?.toString().orEmpty()
        val error = DispatchRules.validateLiters(raw, p.liters)
        if (error != null) {
            binding.tilLiters.error = error
            return
        }
        val liters: BigDecimal = DispatchRules.parseLiters(raw) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_confirm_dispatch)
            .setMessage("${p.folio}\n${p.fuel.label} · ${Money.liters(liters)}\nEsta acción no se puede deshacer.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_dispatch) { _, _ -> confirm(raw) }
            .show()
    }

    private fun confirm(litersRaw: String) {
        val user = container.auth.currentUser() ?: return expireSession()
        binding.btnConfirm.isEnabled = false // evita doble toque
        when (val r = container.dispatchService.confirm(user, folio, litersRaw)) {
            is DispatchOutcome.Done -> {
                payment = null
                showMessage(R.drawable.ic_check_circle, R.string.ticket_done,
                    "El despacho quedó registrado en tu sucursal.",
                    container.payments.byFolio(folio), r.record)
            }
            is DispatchOutcome.Rejected -> {
                binding.btnConfirm.isEnabled = true
                binding.tilLiters.error = r.message
                load()
            }
        }
    }
}
