package com.gavaldon.go.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.gavaldon.go.AppContainer
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.databinding.ActivityRegisterBinding
import com.gavaldon.go.domain.AuthResult
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.PasswordPolicy
import com.gavaldon.go.ui.main.MainActivity
import com.gavaldon.go.util.toCharArraySafe

/** RF-01 — Registro de usuarios: nombre, correo, teléfono y contraseña. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val container: AppContainer
        get() = (application as GavaldonApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
    }

    private fun attemptRegister() {
        listOf(
            binding.tilName, binding.tilEmail, binding.tilPhone,
            binding.tilPassword, binding.tilPasswordConfirm
        ).forEach { it.error = null }

        val name = binding.etName.text?.toString().orEmpty()
        val email = binding.etEmail.text?.toString().orEmpty()
        val phone = binding.etPhone.text?.toString().orEmpty()
        val password = binding.etPassword.text.toCharArraySafe()
        val confirm = binding.etPasswordConfirm.text.toCharArraySafe()

        var firstInvalid = false
        fun fail(layout: com.google.android.material.textfield.TextInputLayout, message: String?) {
            if (message != null) {
                layout.error = message
                firstInvalid = true
            }
        }
        fail(binding.tilName, InputValidator.validateName(name))
        fail(binding.tilEmail, InputValidator.validateEmail(email))
        fail(binding.tilPhone, InputValidator.validatePhone(phone))
        fail(binding.tilPassword, PasswordPolicy.validate(password))
        if (!password.contentEquals(confirm)) {
            fail(binding.tilPasswordConfirm, "Las contraseñas no coinciden.")
        }
        confirm.fill('\u0000')

        if (firstInvalid) {
            password.fill('\u0000')
            return
        }

        setBusy(true)
        container.io.execute {
            val result = container.auth.register(name, email, phone, password)
            password.fill('\u0000')
            container.main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                when (result) {
                    is AuthResult.Success -> {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                    is AuthResult.Failure -> {
                        // El único fallo posible aquí que no cubre la UI es "correo ya registrado".
                        binding.tilEmail.error = result.message
                    }
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !busy
    }
}
