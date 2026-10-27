package com.gavaldon.go

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.gavaldon.go.data.LocalAuthRepository
import com.gavaldon.go.data.LocalDispatchRepository
import com.gavaldon.go.data.LocalPaymentRepository
import com.gavaldon.go.data.LocalPromotionRepository
import com.gavaldon.go.data.LocalStationRepository
import com.gavaldon.go.data.SandboxPaymentGateway
import com.gavaldon.go.domain.AuthRepository
import com.gavaldon.go.domain.DispatchRepository
import com.gavaldon.go.domain.DispatchService
import com.gavaldon.go.domain.PaymentGateway
import com.gavaldon.go.domain.PaymentRepository
import com.gavaldon.go.domain.PromotionRepository
import com.gavaldon.go.domain.StationRepository
import com.gavaldon.go.security.SecureStore
import com.gavaldon.go.util.NotificationHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Localizador de dependencias. Único lugar que conoce las implementaciones concretas. */
class AppContainer(app: Application) {
    private val authStore = SecureStore(app, "gavaldon_auth")
    private val dataStore = SecureStore(app, "gavaldon_data")

    val stations: StationRepository = LocalStationRepository()
    val promotions: PromotionRepository = LocalPromotionRepository()
    val auth: AuthRepository = LocalAuthRepository(authStore, stations.stations().map { it.id }.toSet())
    val payments: PaymentRepository = LocalPaymentRepository(dataStore)
    val dispatches: DispatchRepository = LocalDispatchRepository(dataStore)
    val dispatchService = DispatchService(auth, payments, dispatches)
    val gateway: PaymentGateway = SandboxPaymentGateway()

    /** Hilo de fondo para trabajo lento (PBKDF2, red simulada). Nunca bloquear el hilo principal. */
    val io: ExecutorService = Executors.newFixedThreadPool(2)
    val main = Handler(Looper.getMainLooper())
}

class GavaldonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // ET-UI-001 PEN-02: el modo oscuro no está definido por el propietario; se fuerza el modo claro.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
    }
}
