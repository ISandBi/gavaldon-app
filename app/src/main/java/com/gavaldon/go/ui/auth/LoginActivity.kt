package com.gavaldon.go.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gavaldon.go.AppContainer
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityLoginBinding
import com.gavaldon.go.domain.AuthResult
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.Role
import com.gavaldon.go.ui.dispatcher.DispatcherActivity
import com.gavaldon.go.ui.main.MainActivity
import com.gavaldon.go.util.toCharArraySafe

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXPIRED = "expired"
        const val EXTRA_ROLE = "role"
    }

    private var role = Role.CUSTOMER

    private lateinit var binding: ActivityLoginBinding
    private val container: AppContainer
        get() = (application as GavaldonApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (container.auth.isSessionValid()) {
            val current = container.auth.currentUser()
            if (current != null) {
                goHome(current.role)
                return
            }
        }
        role = runCatching { Role.valueOf(intent.getStringExtra(EXTRA_ROLE).orEmpty()) }
            .getOrDefault(Role.CUSTOMER)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvNotice.visibility =
            if (intent.getBooleanExtra(EXTRA_EXPIRED, false)) View.VISIBLE else View.GONE

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnRegister.setOnClickListener {
            val target = if (role == Role.DISPATCHER) RegisterDispatcherActivity::class.java
            else RegisterActivity::class.java
            startActivity(Intent(this, target))
        }
        binding.btnSwitchRole.setOnClickListener {
            role = if (role == Role.CUSTOMER) Role.DISPATCHER else Role.CUSTOMER
            applyRole()
        }
        applyRole()
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
    }

    private fun attemptLogin() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text.toCharArraySafe()

        val emailError = InputValidator.validateEmail(email)
        if (emailError != null) {
            binding.tilEmail.error = emailError
            password.fill('\u0000')
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Escribe tu contraseña."
            return
        }

        setBusy(true)
        // PBKDF2 es lento a propósito: se ejecuta fuera del hilo principal.
        container.io.execute {
            val result = container.auth.login(email, password, role)
            password.fill('\u0000')
            container.main.post {
                if (isFinishing || isDestroyed) return@post
                setBusy(false)
                when (result) {
                    is AuthResult.Success -> goHome(result.user.role)
                    is AuthResult.Failure -> {
                        binding.tilPassword.error = result.message
                        binding.etPassword.text?.clear()
                    }
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !busy
        binding.btnRegister.isEnabled = !busy
        binding.btnSwitchRole.isEnabled = !busy
    }

    private fun applyRole() {
        val dispatcher = role == Role.DISPATCHER
        binding.tvTitle.setText(if (dispatcher) R.string.login_title_dispatcher else R.string.login_title)
        binding.btnRegister.setText(if (dispatcher) R.string.action_register_dispatcher else R.string.action_register)
        binding.btnSwitchRole.setText(if (dispatcher) R.string.action_switch_customer else R.string.action_switch_dispatcher)
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.etPassword.text?.clear()
    }

    private fun goHome(userRole: Role) {
        val target = if (userRole == Role.DISPATCHER) DispatcherActivity::class.java else MainActivity::class.java
        startActivity(
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
