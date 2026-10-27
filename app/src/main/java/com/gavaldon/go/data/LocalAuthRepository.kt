package com.gavaldon.go.data

import com.gavaldon.go.domain.AuthRepository
import com.gavaldon.go.domain.AuthResult
import com.gavaldon.go.domain.InputValidator
import com.gavaldon.go.domain.PasswordPolicy
import com.gavaldon.go.domain.Role
import com.gavaldon.go.domain.User
import com.gavaldon.go.security.PasswordHasher
import com.gavaldon.go.security.SecureStore
import org.json.JSONObject
import java.util.UUID

/**
 * Cuentas locales cifradas. Stand-in de la API REST (RF-01/02/03).
 *  - OWASP M3: bloqueo tras 5 intentos fallidos (15 min), cierre de sesión a los 15 min de inactividad
 *    (PCI DSS 8.2.8), mensaje de error genérico y verificación de tiempo constante.
 *  - OWASP M4: se vuelve a validar todo aquí aunque la UI ya lo hizo.
 */
class LocalAuthRepository(
    private val store: SecureStore,
    /** Catálogo de sucursales válidas para el alta de despachadores. */
    private val validStationIds: Set<String>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : AuthRepository {

    private companion object {
        const val KEY_USERS = "users"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_SESSION_EMAIL = "session_email"
        const val KEY_SESSION_LAST = "session_last"
        const val MAX_ATTEMPTS = 5
        const val LOCK_MS = 15 * 60 * 1000L
        const val SESSION_TIMEOUT_MS = 15 * 60 * 1000L
        const val TOUCH_THROTTLE_MS = 15_000L
        const val GENERIC_LOGIN_ERROR = "Correo o contraseña incorrectos."
    }

    private val lock = Any()

    @Volatile
    private var lastTouchMem = 0L

    // Hash señuelo: igualamos el tiempo de respuesta cuando el correo no existe.
    private val dummyHash: String by lazy { PasswordHasher.hash("senuelo-no-usar-123".toCharArray()) }

    override fun register(name: String, email: String, phone: String, password: CharArray): AuthResult =
        create(name, email, phone, password, Role.CUSTOMER, null)

    override fun registerDispatcher(
        name: String, email: String, phone: String, password: CharArray, stationId: String
    ): AuthResult {
        if (stationId !in validStationIds) return AuthResult.Failure("Elige una sucursal válida.")
        return create(name, email, phone, password, Role.DISPATCHER, stationId)
    }

    private fun create(
        name: String, email: String, phone: String, password: CharArray, role: Role, stationId: String?
    ): AuthResult {
        val error = InputValidator.validateName(name)
            ?: InputValidator.validateEmail(email)
            ?: InputValidator.validatePhone(phone)
            ?: PasswordPolicy.validate(password)
        if (error != null) return AuthResult.Failure(error)

        val normEmail = InputValidator.normalizeEmail(email)
        val normPhone = InputValidator.normalizePhone(phone)
            ?: return AuthResult.Failure("El teléfono debe tener 10 dígitos.")
        val hash = PasswordHasher.hash(password) // lento a propósito; fuera del candado

        return synchronized(lock) {
            val users = readObject(KEY_USERS)
            if (users.has(normEmail)) {
                AuthResult.Failure("Este correo ya está registrado. Inicia sesión.")
            } else if (stationId != null && stationTaken(users, stationId)) {
                AuthResult.Failure("Esta sucursal ya tiene un despachador registrado.")
            } else {
                val user = JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", name.trim())
                    .put("phone", normPhone)
                    .put("hash", hash)
                    .put("role", role.name)
                if (stationId != null) user.put("stationId", stationId)
                users.put(normEmail, user)
                store.putString(KEY_USERS, users.toString())
                startSession(normEmail)
                AuthResult.Success(toUser(normEmail, user))
            }
        }
    }

    override fun isStationTaken(stationId: String): Boolean =
        synchronized(lock) { stationTaken(readObject(KEY_USERS), stationId) }

    private fun stationTaken(users: JSONObject, stationId: String): Boolean {
        val keys = users.keys()
        while (keys.hasNext()) {
            val u = users.optJSONObject(keys.next()) ?: continue
            if (u.optString("role") == Role.DISPATCHER.name && u.optString("stationId") == stationId) return true
        }
        return false
    }

    override fun displayName(userId: String): String? = synchronized(lock) {
        val users = readObject(KEY_USERS)
        val keys = users.keys()
        while (keys.hasNext()) {
            val u = users.optJSONObject(keys.next()) ?: continue
            if (u.optString("id") == userId) return@synchronized u.optString("name")
        }
        null
    }

    override fun login(email: String, password: CharArray, role: Role): AuthResult {
        val normEmail = InputValidator.normalizeEmail(email)
        lockMessage(normEmail)?.let { return AuthResult.Failure(it) }

        val userJson = synchronized(lock) { readObject(KEY_USERS).optJSONObject(normEmail) }
        val ok = if (userJson != null) {
            PasswordHasher.verify(password, userJson.optString("hash"))
        } else {
            PasswordHasher.verify(password, dummyHash)
            false
        }
        // Una cuenta de otro perfil falla igual que una contraseña errónea (no revela que el correo existe).
        val roleOk = userJson != null && roleOf(userJson) == role

        if (!ok || !roleOk || userJson == null) {
            recordFailure(normEmail)
            return AuthResult.Failure(lockMessage(normEmail) ?: GENERIC_LOGIN_ERROR)
        }
        clearFailures(normEmail)
        startSession(normEmail)
        return AuthResult.Success(toUser(normEmail, userJson))
    }

    private fun roleOf(json: JSONObject): Role =
        runCatching { Role.valueOf(json.optString("role", Role.CUSTOMER.name)) }.getOrDefault(Role.CUSTOMER)

    override fun currentUser(): User? {
        val email = store.getString(KEY_SESSION_EMAIL) ?: return null
        val json = synchronized(lock) { readObject(KEY_USERS).optJSONObject(email) } ?: return null
        return toUser(email, json)
    }

    override fun isSessionValid(): Boolean {
        if (store.getString(KEY_SESSION_EMAIL) == null) return false
        val last = maxOf(lastTouchMem, store.getLong(KEY_SESSION_LAST))
        return clock() - last in 0 until SESSION_TIMEOUT_MS
    }

    override fun touchSession() {
        val now = clock()
        if (now - lastTouchMem < TOUCH_THROTTLE_MS) return
        if (store.getString(KEY_SESSION_EMAIL) == null) return
        lastTouchMem = now
        store.putLong(KEY_SESSION_LAST, now)
    }

    override fun logout() {
        store.remove(KEY_SESSION_EMAIL)
        store.remove(KEY_SESSION_LAST)
        lastTouchMem = 0L
    }

    // ---- internos ----

    private fun startSession(email: String) {
        val now = clock()
        store.putString(KEY_SESSION_EMAIL, email)
        store.putLong(KEY_SESSION_LAST, now)
        lastTouchMem = now
    }

    private fun toUser(email: String, json: JSONObject) = User(
        id = json.optString("id"),
        name = json.optString("name"),
        email = email,
        phone = json.optString("phone"),
        role = roleOf(json),
        stationId = json.optString("stationId").ifEmpty { null }
    )

    private fun readObject(key: String): JSONObject =
        try {
            JSONObject(store.getString(key) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    private fun lockMessage(email: String): String? = synchronized(lock) {
        val entry = readObject(KEY_ATTEMPTS).optJSONObject(email) ?: return@synchronized null
        val until = entry.optLong("until", 0L)
        val remaining = until - clock()
        if (remaining > 0) {
            val minutes = (remaining + 59_999) / 60_000
            "Demasiados intentos fallidos. Inténtalo de nuevo en $minutes min."
        } else null
    }

    private fun recordFailure(email: String) = synchronized(lock) {
        val all = readObject(KEY_ATTEMPTS)
        val entry = all.optJSONObject(email) ?: JSONObject()
        val expired = entry.optLong("until", 0L).let { it != 0L && it <= clock() }
        val count = (if (expired) 0 else entry.optInt("count", 0)) + 1
        entry.put("count", count)
        entry.put("until", if (count >= MAX_ATTEMPTS) clock() + LOCK_MS else 0L)
        all.put(email, entry)
        store.putString(KEY_ATTEMPTS, all.toString())
    }

    private fun clearFailures(email: String) = synchronized(lock) {
        val all = readObject(KEY_ATTEMPTS)
        if (all.has(email)) {
            all.remove(email)
            store.putString(KEY_ATTEMPTS, all.toString())
        }
    }
}
