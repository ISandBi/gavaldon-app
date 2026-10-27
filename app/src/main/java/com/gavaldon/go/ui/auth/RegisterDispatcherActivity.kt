package com.gavaldon.go.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.gavaldon.go.AppContainer
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityRegisterDispatcherBinding
import com.gavaldon.go.domain.AuthResult
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.PasswordPolicy
import com.gavaldon.go.domain.Station
import com.gavaldon.go.ui.dispatcher.DispatcherActivity
import com.gavaldon.go.util.toCharArraySafe
import com.google.android.material.textfield.TextInputLayout

/** Alta de despachador: un despachador por sucursal; solo se ofrecen las sucursales libres. */
class RegisterDispatcherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterDispatcherBinding
    private val container: AppContainer
        get() = (application as GavaldonApp).container

    private var freeStations: List<Station> = emptyList()
    private var selected: Station? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterDispatcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        loadStations()
        binding.etStation.setOnItemClickListener { _, _, position, _ ->
            selected = freeStations.getOrNull(position)
            binding.tilStation.error = null
        }
        binding.btnRegister.setOnClickListener { attemptRegister() }
    }

    private fun loadStations() {
        freeStations = container.stations.stations().filter { !container.auth.isStationTaken(it.id) }
        selected = selected?.takeIf { s -> freeStations.any { it.id == s.id } }
        binding.etStation.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, freeStations.map { it.name })
        )
        if (freeStations.isEmpty()) {
            binding.tilStation.error = getString(R.string.register_station_none)
            binding.btnRegister.isEnabled = false
        }
    }

    private fun attemptRegister() {
        listOf(
            binding.tilStation, binding.tilName, binding.tilEmail, binding.tilPhone,
            binding.tilPassword, binding.tilPasswordConfirm
        ).forEach { it.error = null }

        val name = binding.etName.text?.toString().orEmpty()
        val email = binding.etEmail.text?.toString().orEmpty()
        val phone = binding.etPhone.text?.toString().orEmpty()
        val password = binding.etPassword.text.toCharArraySafe()
        val confirm = binding.etPasswordConfirm.text.toCharArraySafe()
        val station = selected

        var invalid = false
        fun fail(layout: TextInputLayout, message: String?) {
            if (message != null) {
                layout.error = message
                invalid = true
            }
        }
        fail(binding.tilStation, if (station == null) "Elige tu sucursal." else null)
        fail(binding.tilName, InputValidator.validateName(name))
        fail(binding.tilEmail, InputValidator.validateEmail(email))
        fail(binding.tilPhone, InputValidator.validatePhone(phone))
        fail(binding.tilPassword, PasswordPolicy.validate(password))
        if (!password.contentEquals(confirm)) fail(binding.tilPasswordConfirm, "Las contraseñas no coinciden.")
        confirm.fill('\u0000')

        if (invalid || station == null) {
            password.fill('\u0000')
            return
        }

        setBusy(true)
        container.io.execute {
            val result = container.auth.registerDispatcher(name, email, phone, password, station.id)
            password.fill('\u0000')
            container.main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                when (result) {
                    is AuthResult.Success -> {
                        startActivity(
                            Intent(this, DispatcherActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                    is AuthResult.Failure -> {
                        // Correo repetido, sucursal ya tomada por otro despachador, etc.
                        val target = if (result.message.contains("sucursal", ignoreCase = true))
                            binding.tilStation else binding.tilEmail
                        target.error = result.message
                        loadStations()
                    }
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !busy && freeStations.isNotEmpty()
    }
}
