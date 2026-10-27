package com.gavaldon.go.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import com.gavaldon.go.BuildConfig

/**
 * OWASP M7 (Insufficient Binary Protections): en builds de release no se procesan pagos si hay un
 * depurador conectado o la app fue reempaquetada como depurable.
 */
object RuntimeGuards {
    fun isPaymentEnvironmentSafe(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return !debuggable && !Debug.isDebuggerConnected()
    }
}
