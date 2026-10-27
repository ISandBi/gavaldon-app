package com.gavaldon.go.ui.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.gavaldon.go.AppContainer
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.domain.Role
import com.gavaldon.go.ui.auth.LoginActivity

/**
 * Base de toda pantalla que requiere sesión (RF-03, OWASP M3, PCI DSS 8.2.8):
 * si la sesión expiró (15 min sin actividad) o no existe, se vuelve al inicio de sesión.
 */
abstract class SecureActivity : AppCompatActivity() {

    protected val container: AppContainer
        get() = (application as GavaldonApp).container

    /** Perfil que puede ver esta pantalla; otro perfil con sesión válida es redirigido (OWASP M3). */
    protected open val requiredRole: Role = Role.CUSTOMER

    override fun onResume() {
        super.onResume()
        if (!container.auth.isSessionValid()) {
            expireSession()
        } else if (container.auth.currentUser()?.role != requiredRole) {
            goToLogin(expired = false)
        } else {
            container.auth.touchSession()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.auth.touchSession()
    }

    protected fun expireSession() {
        container.auth.logout()
        goToLogin(expired = true)
    }

    protected fun goToLogin(expired: Boolean) {
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_EXPIRED, expired)
            .putExtra(LoginActivity.EXTRA_ROLE, requiredRole.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
