package com.gavaldon.go.ui.pay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityPaymentBinding
import com.gavaldon.go.domain.CardData
import com.gavaldon.go.domain.CardValidator
import com.gavaldon.go.domain.ChargeRequest
import com.gavaldon.go.domain.ChargeResult
import com.gavaldon.go.domain.FuelType
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PaymentRecord
import com.gavaldon.go.domain.PaymentStatus
import com.gavaldon.go.domain.Station
import com.gavaldon.go.domain.TokenizeResult
import com.gavaldon.go.security.RuntimeGuards
import com.gavaldon.go.ui.base.SecureActivity
import com.gavaldon.go.util.NotificationHelper
import com.gavaldon.go.util.afterTextChanged
import com.gavaldon.go.util.formatAsCardNumber
import com.gavaldon.go.util.formatAsExpiry
import java.math.BigDecimal
import java.util.UUID

/**
 * RF-07..RF-10: compra de combustible con tarjeta de crédito/débito.
 *
 * Controles PCI DSS / OWASP aplicados en esta pantalla:
 *  - FLAG_SECURE: sin capturas de pantalla ni miniatura en "recientes" (PCI 3.4 / M9).
 *  - Campos de tarjeta sin autofill, sin sugerencias y sin guardar estado de vista (saveEnabled=false).
 *  - CVV nunca se guarda ni se registra; PAN completo nunca se guarda (solo marca + últimos 4).
 *  - La tarjeta se convierte en token y el cobro usa el token (RF-08).
 *  - Se limpian los campos al enviar y al salir de la pantalla.
 *  - Clave de idempotencia para impedir doble cobro; botón bloqueado durante el proceso.
 *  - Tapjacking: filterTouchesWhenObscured en la raíz y en el botón de pago (M8).
 *  - Extras del Intent validados contra el catálogo (M4).
 */
class PaymentActivity : SecureActivity() {

    companion object {
        private const val EXTRA_STATION = "station_id"
        private const val EXTRA_FUEL = "fuel"
        private val QUICK_AMOUNTS = listOf(100, 200, 500, 1000)

        fun newIntent(context: Context, stationId: String, fuel: FuelType): Intent =
            Intent(context, PaymentActivity::class.java)
                .putExtra(EXTRA_STATION, stationId)
                .putExtra(EXTRA_FUEL, fuel.name)
    }

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var station: Station
    private lateinit var fuel: FuelType
    private lateinit var pricePerLiter: BigDecimal
    private var idempotencyKey: String = UUID.randomUUID().toString()
    private var paying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debe aplicarse antes de crear el contenido.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)

        val st = container.stations.station(intent.getStringExtra(EXTRA_STATION).orEmpty())
        val fuelType = runCatching { FuelType.valueOf(intent.getStringExtra(EXTRA_FUEL).orEmpty()) }.getOrNull()
        val price = if (st != null && fuelType != null) {
            container.stations.prices(st.id).firstOrNull { it.fuel == fuelType }
        } else null
        if (st == null || fuelType == null || price == null) {
            finish()
            return
        }
        station = st
        fuel = fuelType
        pricePerLiter = price.pricePerLiter

        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvStation.text = station.name
        binding.tvFuelPrice.text =
            "${fuel.label} · ${Money.price(pricePerLiter)} ${getString(R.string.per_liter)}"

        setupAmount()
        binding.etPan.formatAsCardNumber()
        binding.etExpiry.formatAsExpiry()
        binding.btnPay.setOnClickListener { onPayClicked() }
        updateSummary()
    }

    private fun setupAmount() {
        QUICK_AMOUNTS.forEach { amount ->
            val chip = Chip(this)
            chip.text = Money.pesos(BigDecimal(amount)).substringBefore('.')
            chip.setOnClickListener { binding.etAmount.setText(amount.toString()) }
            binding.chipsAmount.addView(chip)
        }
        binding.etAmount.afterTextChanged {
            binding.tilAmount.error = null
            updateSummary()
        }
    }

    private fun updateSummary() {
        val amount = InputValidator.parseAmount(binding.etAmount.text?.toString().orEmpty())
        if (amount != null) {
            val liters = Money.litersFor(amount, pricePerLiter)
            binding.tvLiters.text = getString(R.string.payment_liters_estimate, Money.liters(liters))
            binding.tvTotal.text = getString(R.string.payment_total, Money.pesos(amount))
        } else {
            binding.tvLiters.text = ""
            binding.tvTotal.text = getString(R.string.payment_total, Money.pesos(BigDecimal.ZERO))
        }
        binding.btnPay.isEnabled = amount != null && !paying
    }

    private fun onPayClicked() {
        if (paying) return
        if (!RuntimeGuards.isPaymentEnvironmentSafe(this)) {
            Toast.makeText(this, R.string.payment_env_unsafe, Toast.LENGTH_LONG).show()
            return
        }

        listOf(binding.tilAmount, binding.tilHolder, binding.tilPan, binding.tilExpiry, binding.tilCvv)
            .forEach { it.error = null }

        val amountText = binding.etAmount.text?.toString().orEmpty()
        val holder = binding.etHolder.text?.toString().orEmpty()
        val panDigits = CardValidator.digitsOnly(binding.etPan.text?.toString().orEmpty())
        val expiry = binding.etExpiry.text?.toString().orEmpty()
        val cvv = binding.etCvv.text?.toString().orEmpty()
        val brand = CardValidator.brandOf(panDigits)

        var invalid = false
        fun check(layout: TextInputLayout, error: String?) {
            if (error != null) {
                layout.error = error
                invalid = true
            }
        }
        check(binding.tilAmount, InputValidator.validateAmount(amountText))
        check(binding.tilHolder, CardValidator.validateHolder(holder))
        check(binding.tilPan, CardValidator.validatePan(panDigits))
        check(binding.tilExpiry, CardValidator.validateExpiry(expiry))
        check(binding.tilCvv, CardValidator.validateCvv(cvv, brand))
        if (invalid) return

        val amount = InputValidator.parseAmount(amountText) ?: return
        val (month, year) = CardValidator.parseExpiry(expiry) ?: return
        val card = CardData(holder.trim(), panDigits, month, year, cvv)
        val user = container.auth.currentUser()
        if (user == null) {
            expireSession()
            return
        }

        clearCardFields() // el dato sale de la pantalla en cuanto se envía
        setPaying(true)

        container.io.execute {
            val outcome = processPayment(card, amount)
            val record = outcome.first?.let { status ->
                PaymentRecord(
                    id = UUID.randomUUID().toString(),
                    folio = container.payments.nextFolio(),
                    userId = user.id,
                    stationId = station.id,
                    stationName = station.name,
                    fuel = fuel,
                    liters = Money.litersFor(amount, pricePerLiter),
                    amount = amount,
                    status = status,
                    providerRef = outcome.second,
                    createdAtEpochMs = System.currentTimeMillis(),
                    cardBrand = brand.label,
                    cardLast4 = panDigits.takeLast(4)
                ).also { container.payments.add(it) }
            }
            container.main.post { onPaymentFinished(record, outcome.third) }
        }
    }

    /** @return (estado o null si hubo error técnico, referencia, mensaje de error) */
    private fun processPayment(card: CardData, amount: BigDecimal): Triple<PaymentStatus?, String, String> {
        return when (val token = container.gateway.tokenize(card)) {
            is TokenizeResult.Failure -> Triple(null, "", token.message)
            is TokenizeResult.Success -> {
                val request = ChargeRequest(
                    token = token.token,
                    amount = amount,
                    currency = "MXN",
                    idempotencyKey = idempotencyKey,
                    description = "Combustible ${fuel.label} - ${station.name}"
                )
                when (val charge = container.gateway.charge(request)) {
                    is ChargeResult.Approved -> Triple(PaymentStatus.APPROVED, charge.reference, "")
                    is ChargeResult.Declined -> Triple(PaymentStatus.REJECTED, charge.reference, "")
                    is ChargeResult.Pending -> Triple(PaymentStatus.PENDING, charge.reference, "")
                    is ChargeResult.Error -> Triple(null, "", charge.message)
                }
            }
        }
    }

    private fun onPaymentFinished(record: PaymentRecord?, errorMessage: String) {
        if (isFinishing || isDestroyed) return
        setPaying(false)
        if (record == null) {
            idempotencyKey = UUID.randomUUID().toString() // el intento falló antes del cobro
            binding.tilCvv.error = errorMessage.ifEmpty { "No se pudo procesar el pago. Inténtalo de nuevo." }
            return
        }
        if (record.status == PaymentStatus.APPROVED) {
            NotificationHelper.notifyPaymentApproved(this, record.folio, record.stationName)
        }
        startActivity(PaymentResultActivity.newIntent(this, record))
        finish()
    }

    private fun setPaying(value: Boolean) {
        paying = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnPay.isEnabled = !value && InputValidator.parseAmount(
            binding.etAmount.text?.toString().orEmpty()
        ) != null
    }

    private fun clearCardFields() {
        binding.etPan.text?.clear()
        binding.etCvv.text?.clear()
        binding.etExpiry.text?.clear()
        binding.etHolder.text?.clear()
    }

    override fun onStop() {
        // Si la app pasa a segundo plano no se conservan datos de tarjeta en pantalla.
        if (::binding.isInitialized && !paying) clearCardFields()
        super.onStop()
    }
}
