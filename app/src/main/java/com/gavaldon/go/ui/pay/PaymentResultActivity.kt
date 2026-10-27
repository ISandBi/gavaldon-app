package com.gavaldon.go.ui.pay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityPaymentResultBinding
import com.gavaldon.go.domain.CardValidator
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.PaymentRecord
import com.gavaldon.go.domain.PaymentStatus
import com.gavaldon.go.ui.base.SecureActivity
import com.gavaldon.go.ui.main.MainActivity

/** RF-10: informa si el pago fue aprobado, rechazado o quedó pendiente. Solo recibe datos no sensibles. */
class PaymentResultActivity : SecureActivity() {

    companion object {
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_FOLIO = "folio"
        private const val EXTRA_STATION = "station"
        private const val EXTRA_FUEL = "fuel"
        private const val EXTRA_LITERS = "liters"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_REF = "ref"
        private const val EXTRA_CARD = "card"

        fun newIntent(context: Context, record: PaymentRecord): Intent =
            Intent(context, PaymentResultActivity::class.java)
                .putExtra(EXTRA_STATUS, record.status.name)
                .putExtra(EXTRA_FOLIO, record.folio)
                .putExtra(EXTRA_STATION, record.stationName)
                .putExtra(EXTRA_FUEL, record.fuel.label)
                .putExtra(EXTRA_LITERS, Money.liters(record.liters))
                .putExtra(EXTRA_AMOUNT, Money.pesos(record.amount))
                .putExtra(EXTRA_REF, record.providerRef)
                .putExtra(EXTRA_CARD, "${record.cardBrand} ${CardValidator.maskLast4(record.cardLast4)}")
    }

    private lateinit var binding: ActivityPaymentResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val status = runCatching {
            PaymentStatus.valueOf(intent.getStringExtra(EXTRA_STATUS).orEmpty())
        }.getOrNull()
        if (status == null) {
            finish()
            return
        }

        // El estado se comunica con icono + texto, nunca solo con color (criterio 1.4.1).
        val (icon, message) = when (status) {
            PaymentStatus.APPROVED -> R.drawable.ic_check_circle to R.string.result_approved_msg
            PaymentStatus.REJECTED -> R.drawable.ic_cancel to R.string.result_rejected_msg
            PaymentStatus.PENDING -> R.drawable.ic_schedule to R.string.result_pending_msg
        }
        binding.imgStatus.setImageResource(icon)
        binding.tvStatus.text = status.label
        binding.tvMessage.setText(message)

        fun extra(key: String) = intent.getStringExtra(key).orEmpty()
        binding.tvFolio.text = getString(R.string.result_folio, extra(EXTRA_FOLIO))
        binding.tvStation.text = getString(R.string.result_station, extra(EXTRA_STATION))
        binding.tvFuel.text = getString(R.string.result_fuel, extra(EXTRA_FUEL))
        binding.tvLiters.text = getString(R.string.result_liters, extra(EXTRA_LITERS))
        binding.tvAmount.text = getString(R.string.result_amount, extra(EXTRA_AMOUNT))
        binding.tvReference.text = getString(R.string.result_reference, extra(EXTRA_REF))
        binding.tvCard.text = getString(R.string.result_card, extra(EXTRA_CARD))

        binding.btnHome.setOnClickListener { openMain(null) }
        binding.btnHistory.setOnClickListener { openMain(MainActivity.TAB_HISTORY) }
    }

    private fun openMain(tab: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (tab != null) intent.putExtra(MainActivity.EXTRA_TAB, tab)
        startActivity(intent)
        finish()
    }
}
