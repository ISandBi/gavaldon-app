# Gavaldon GO! — App Android

App móvil para clientes de GAVALDON S.A. de C.V.: mapa de las 10 estaciones, precios, compra de combustible con tarjeta, historial y promociones.

## Cómo abrirla y ejecutarla

**Especificaciones del proyecto**

| Campo | Valor |
|---|---|
| Lenguaje / UI | Kotlin 2.0.21, vistas XML + ViewBinding, RecyclerView |
| `applicationId` / namespace | `com.gavaldon.go` |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 35 / 35 |
| Android Gradle Plugin / Gradle | 8.7.3 / 8.9 (wrapper incluido) |
| JDK | 17 (el que trae Android Studio sirve) |
| Android Studio | Ladybug (2024.2.1) o más reciente |
| Mapa | osmdroid 6.1.20 (OpenStreetMap, sin API key) |

**Pasos**

1. Android Studio → *Open* → elegir la carpeta `GavaldonGo`.
2. Esperar la sincronización de Gradle (necesita internet la primera vez). Si pregunta por actualizar AGP/Gradle, puede aceptar o ignorar.
3. Elegir un emulador (API 26+ con servicios de Google) o un teléfono con depuración USB, y pulsar **Run ▶**.
4. APK sin ejecutar: *Build → Build Bundle(s)/APK(s) → Build APK(s)*. Queda en `app/build/outputs/apk/debug/app-debug.apk`.
5. Sin Android Studio: subir la carpeta a GitHub; el workflow `.github/workflows/build-apk.yml` compila y publica el APK como artefacto.

El dispositivo necesita internet para descargar los mosaicos del mapa.

**Primer uso:** *Crear cuenta* (contraseña de 12+ caracteres con letras y números) → mapa → tocar un pin → *Comprar* → usar una tarjeta de prueba de `docs/SEGURIDAD_OWASP_PCI.md`.

## Qué hace

- **Mapa** con las 10 estaciones y sus coordenadas. Al tocar un pin se abre una hoja inferior con un **RecyclerView**: detalle de la estación (nombre, ubicación DMS, latitud/longitud), un renglón por combustible con precio y botón **Comprar**, y **Cómo llegar**.
- **Ver en lista / mapa**, **Mi ubicación** (permiso solo al tocarlo) y aviso de la **gasolinera más cercana**.
- **Pago** con tarjeta de crédito/débito (Visa, Mastercard, Amex): monto, litros estimados, validación completa, resultado aprobado / rechazado / pendiente con folio `TCK-AAAA-######`.
- **Historial** de pagos, **Promociones** vigentes, **Perfil** y cierre de sesión.

## Perfil de despachador

En el login, **Acceso para despachadores** → **Registrar despachador**: se elige la sucursal (solo las libres; **un despachador por sucursal**), queda ligada a la cuenta y solo ese despachador entra a ella.

- **Inicio**: contadores de la sucursal (por despachar, pagos en espera, aprobados y despachos de hoy, ventas, litros) y **validar ticket por folio**.
- **Ticket**: casos aprobado / de otra sucursal / pago pendiente o rechazado / ya despachado / no encontrado. Se registran los litros realmente despachados (máx. lo pagado) con la diferencia, confirmación y pantalla de despacho completado. Un ticket no puede despacharse dos veces.
- **Compras**: todas las compras de SU sucursal, con filtros (todas, por despachar, despachadas, pago pendiente, rechazadas); tocar una abre el ticket.
- **Despachos**: historial Hoy / Semana / Mes con totales.
- **Corte**: resumen del turno, aviso de tickets sin despachar, cierre de turno y cortes anteriores.
- **Perfil**: datos, sucursal y cierre de sesión.

**Límite importante:** sin backend, compras y despachos viven en el mismo teléfono; para probar, compra como cliente y luego entra como despachador de esa misma estación en el mismo dispositivo. Con la API REST funcionará entre dispositivos (las reglas están en `DispatchService`, pensadas para ejecutarse en el servidor). El alta es por autoservicio; en producción debe hacerla un administrador, y no hay recuperación de contraseña.

## Estado honesto

- **No se pudo compilar ni probar en el entorno donde se escribió** (sin acceso a los repositorios de Google/Maven). La estructura de recursos, XML, referencias e ids fue verificada con scripts y el código revisado, pero **la primera compilación en Android Studio puede arrojar errores menores que habrá que corregir**. Las pruebas unitarias (`app/src/test`) cubren validación de tarjeta, contraseñas, hash, distancia y litros.
- **Pasarela de pagos simulada**; **backend inexistente** (datos locales cifrados); **push FCM pendiente**; promociones de ejemplo; precios iguales en todas las estaciones (los del wireframe). Detalle en `docs/SEGURIDAD_OWASP_PCI.md`.
- **HAL-01 sin resolver por el propietario**: el botón principal usa provisionalmente la opción A (texto azul sobre naranja, 5.39:1). Se cambia editando 2 líneas en `res/values/colors.xml`.
- **Pendientes de ET-UI-001**: PEN-01 (colores de éxito/advertencia: se usan iconos + texto), PEN-02 (modo oscuro: se fuerza claro), PEN-04 (tipografía: fuente del sistema). Reporte de contraste en `docs/REPORTE_CONTRASTE.md`; la prueba de campo CA-06 requiere dispositivo físico.
- El wireframe muestra calificación (4.7) y fotos; no se incluyeron porque no hay datos reales. La imagen de la estación es una ilustración vectorial.

## Arquitectura (SOLID)

`domain/` (modelos, contratos, validaciones puras) → `data/` (implementaciones locales y sandbox) → `ui/` (actividades, fragmentos, adaptadores). `AppContainer` es el único lugar que conoce las implementaciones: para pasar a la API REST + MySQL solo se reemplazan clases de `data/`.

## Estructura

```
app/src/main/java/com/gavaldon/go/
  domain/    Models, Contracts, CardValidator, InputValidator, GeoMoney
  data/      Local*Repository, SandboxPaymentGateway
  security/  KeystoreCipher, SecureStore, PasswordHasher, RuntimeGuards
  ui/        auth, main, map (MapFragment, StationSheet, adaptadores), pay, history, promos, profile
  util/      LocationProvider, NotificationHelper, TextWatchers
app/src/main/res/values/colors.xml   <- ÚNICO archivo con colores hexadecimales (tokens ET-UI-001)
docs/        SEGURIDAD_OWASP_PCI.md, REPORTE_CONTRASTE.md
```
