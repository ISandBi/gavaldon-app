# Seguridad: OWASP Mobile Top 10 (2024) y PCI DSS 4.0

> Se interpretó "OWASP 10" como **OWASP Mobile Top 10 (2024)**, por ser una app Android.
> Este documento dice qué hace el código **hoy**, dónde está y qué falta. No es una certificación.

## OWASP Mobile Top 10

| # | Riesgo | Qué se hizo | Dónde | Pendiente / límite |
|---|---|---|---|---|
| M1 | Uso inadecuado de credenciales | Sin llaves ni secretos en el APK. El mapa (OpenStreetMap) no necesita API key. Llave de cifrado dentro del Android Keystore. | `KeystoreCipher`, `app/build.gradle.kts` | Al integrar el proveedor de pagos, usar solo su llave **pública** en la app. |
| M2 | Cadena de suministro | Versiones de dependencias y plugins fijas (sin `+`). Solo 8 librerías, todas de Google/AndroidX u osmdroid. | `build.gradle.kts` | Falta correr un escáner de dependencias (OWASP Dependency-Check) con red. |
| M3 | Autenticación/autorización insegura | Hash PBKDF2-SHA256 600 000 iteraciones con sal, comparación en tiempo constante, mensaje genérico, bloqueo tras 5 intentos (15 min), sesión que expira a los 15 min de inactividad, todas las pantallas internas exigen sesión. | `PasswordHasher`, `LocalAuthRepository`, `SecureActivity` | La autenticación real debe vivir en el servidor (API REST). Considerar biometría para pagos. |
| M4 | Validación insuficiente de entrada/salida | Validación de nombre, correo, teléfono, monto (50–2 000, entero), tarjeta (Luhn, marca, longitud, vencimiento, CVV) y extras de Intent contra el catálogo. Entradas con `maxLength`. Sin SQL ni WebView. | `CardValidator`, `InputValidator`, `PaymentActivity` | El servidor debe repetir toda validación. |
| M5 | Comunicación insegura | `usesCleartextTraffic=false` y `network_security_config` sin tráfico en claro. | `network_security_config.xml` | Agregar *certificate pinning* del dominio de la API cuando exista (plantilla en el archivo). |
| M6 | Controles de privacidad insuficientes | Ubicación solo si el usuario toca "Mi ubicación"; la app funciona sin ella; no se guarda ni envía. Sin analítica ni SDK de terceros. Notificaciones con visibilidad privada. | `LocationProvider`, `MapFragment` | Falta el aviso de privacidad legal (lo redacta GAVALDON). |
| M7 | Protecciones binarias insuficientes | R8 con ofuscación y reducción de recursos en *release*; logs eliminados en *release*; bloqueo de pagos si hay depurador o app depurable en *release*. | `app/build.gradle.kts`, `proguard-rules.pro`, `RuntimeGuards` | No hay detección de root ni *Play Integrity*; evaluar según el riesgo aceptado por GAVALDON. |
| M8 | Configuración de seguridad incorrecta | `allowBackup=false`, reglas de extracción vacías, actividades `exported=false` salvo el lanzador, `PendingIntent` inmutable con Intent explícito, `filterTouchesWhenObscured` en pagos (anti *tapjacking*), `FLAG_SECURE` en pago. | `AndroidManifest.xml`, `PaymentActivity` | — |
| M9 | Almacenamiento inseguro de datos | Todo dato local se cifra con AES-256-GCM (Keystore) antes de escribirse. No se guarda PAN, CVV ni vencimiento. Campos de tarjeta con `saveEnabled=false` y sin autofill. Se limpian al enviar y al ir a segundo plano. | `SecureStore`, `LocalPaymentRepository`, `activity_payment.xml` | Los datos viven en el teléfono solo porque aún no hay backend. |
| M10 | Criptografía insuficiente | AES-256-GCM con IV aleatorio de 12 bytes, llave no exportable; PBKDF2-HMAC-SHA256; `SecureRandom` para sales y tokens. Sin algoritmos propios ni MD5/SHA-1. | `KeystoreCipher`, `PasswordHasher` | — |

## Perfil de despachador (control de acceso)

- Roles `CUSTOMER` / `DISPATCHER`; `SecureActivity.requiredRole` redirige a quien entre a una pantalla de otro perfil.
- `DispatchService` aplica la autorización: un despachador solo consulta y despacha tickets de **su** sucursal; solo pagos APROBADOS; un pago se despacha una sola vez (comprobado bajo candado); litros > 0 y ≤ pagados.
- Login por perfil: una cuenta de otro perfil falla con el mismo mensaje genérico. Al ticket de otra sucursal solo se le dice a qué sucursal pertenece.
- El despachador ve solo nombre del cliente, combustible, litros y monto; nunca datos de tarjeta.
- Pendiente: alta por administrador, bitácora de auditoría y roles en el servidor.

## PCI DSS 4.0 (alcance de la app)

| Requisito | Estado |
|---|---|
| 3.3 / 3.4 No almacenar datos sensibles de autenticación (CVV) y enmascarar el PAN | CVV y PAN nunca se guardan ni se registran; solo marca + últimos 4 dígitos. `CardData.toString()` no revela datos. |
| 4.2.1 Cifrar la transmisión de datos de titular | TLS obligatorio (sin tráfico en claro). El cobro se hace con **token**, no con el número de tarjeta. |
| 6.2 / 6.3 Desarrollo seguro y vulnerabilidades | Validación de entradas, dependencias fijas, pruebas unitarias de la lógica de pago. |
| 8.2.8 Sesión inactiva > 15 min requiere re-autenticación | Implementado (15 min). |
| 8.3.4 Bloqueo por intentos fallidos (máx. 10) | Implementado con 5 intentos / 15 min. |
| 8.3.6 Contraseñas de al menos 12 caracteres con letras y números | Implementado (`PasswordPolicy`). |
| 10 Registro y monitoreo | La app **no** registra datos de tarjeta en logs. El registro de auditoría debe hacerse en el servidor. |

## Lo que NO está cubierto (importante)

1. **La pasarela es simulada** (`SandboxPaymentGateway`). No cobra dinero real. GAVALDON debe elegir un procesador autorizado (RF-07) y sustituir esa clase por su SDK; con el SDK, la tarjeta se captura en campos del proveedor y el PAN ya no pasa por código propio (alcance SAQ A, el más ligero).
2. **No hay cumplimiento PCI "por tener la app"**: la certificación depende de todo el entorno (servidor, procesos, proveedor) y la valida un QSA o un cuestionario SAQ del procesador.
3. **Backend**: la documentación pide API REST + MySQL. Hoy los datos son locales y cifrados. Las interfaces de `domain/Contracts.kt` permiten cambiar a la API sin tocar las pantallas.
4. **Push (RF-21)**: solo hay notificación local al aprobar un pago. Las push de promociones necesitan un proyecto Firebase creado por GAVALDON (`google-services.json`).

## Tarjetas de prueba del sandbox

| Número | Resultado |
|---|---|
| 4111 1111 1111 1111 | Aprobada |
| 4000 0000 0000 0002 | Declinada |
| 4000 0000 0000 0259 | Pendiente |
| 5555 5555 5555 4444 | Aprobada (Mastercard) |
| 3782 822463 10005 | Aprobada (Amex, CVV de 4 dígitos) |

Cualquier vencimiento futuro (p. ej. 12/30) y CVV de 3 dígitos (4 en Amex).
