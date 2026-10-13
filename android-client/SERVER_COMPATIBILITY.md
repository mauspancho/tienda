# Compatibilidad con main — 13 de octubre de 2026

Se revisó `main` del servidor, commit **adf13191538c2372b1389dcfb1325340832a86e6**, y se confirmó con `git ls-remote` que coincide con el main remoto. No se modificó el código del servidor.

## Resultado de la revisión

El servidor ahora es multitienda. `CurrentTenant` deriva la tienda del usuario autenticado; los access/refresh tokens se vinculan al usuario, tienda, roles y estado de revocación. **Android no debe enviar un tenantId ni añadir un header de tienda.** El contrato de login/refresh/me conserva los mismos campos y los tokens continúan siendo opacos para Android.

Se generó el OpenAPI del main en una instancia Spring/H2 aislada, usando un principal de prueba para consultar documentación protegida. El resultado está en `backend-main-openapi.json`. La comparación estructural con `backend-credits-openapi.json` encontró:

- Se conservan todas las rutas anteriores y los modelos de autenticación, productos, compras, caja, POS y ventas.
- `StatementResponse` añade `charges` con cargos manuales: id, date, amount, concept, status, username, applied y balance.
- El servidor añade POST `/api/v1/credits/customers/{id}/charges` y POST `/api/v1/credits/charges/{chargeId}/reverse`.
- Esos endpoints son funciones adicionales; no reemplazan el checkout, los abonos o las reversiones ya usadas por Android.

La consulta anónima a `https://tienda.maus.lat/api-docs` redirige actualmente al login y devuelve HTML, por lo que no se trató esa página como un contrato JSON. Se verificó el código del main remoto coincidente y el OpenAPI generado localmente. No se utilizaron credenciales ni se realizaron operaciones financieras en producción.

## Cambios Android realizados

1. `CreditModels.kt`: modelo `CreditCharge` y `CreditStatement.charges` con lista vacía por defecto para aceptar también respuestas anteriores.
2. `CreditsScreen.kt`: muestra el número de cargos manuales pendientes y su concepto, fecha, importe, abonado, saldo y estado, todos enviados por servidor. Los movimientos siguen mostrando la cronología del servidor. No suma cargos para reconstruir la deuda ni aplica cargos locales.
3. `ApiClient.kt` y `SessionAuthenticator.kt`: cada solicitud autenticada mantiene una etiqueta interna con la identidad de origen. Esa etiqueta no es un header HTTP ni un identificador de tienda. Una petición anterior no puede renovar o tomar credenciales de otra cuenta.
4. `SessionManager.kt`: comprueba identidad al renovar y rechaza un refresh que cambie de usuario. Conserva el refresh sincronizado y la protección contra respuestas tardías.
5. `AppViewModel.kt`: al revocarse la sesión, cancela solicitudes, cierra definitivamente su cliente y crea un cliente nuevo antes de volver al login. El siguiente usuario no comparte el cliente anterior.
6. `validation/GenerateBackendOpenApi.java`: admite documentación protegida en el contexto aislado de prueba.

Se mantuvieron el cobro directo en efectivo, la opción Crédito, escáner/pitido, buscador que se limpia, costo automático de compras, autenticación Bearer y checkpoint cifrado por servidor/usuario/flujo. No se añadió una BD financiera, selección manual de tienda, nuevas dependencias ni un flujo de registro de cargos manuales. Estos cargos se consultan cuando se crean desde la web.

Las cuentas móviles deben pertenecer a una tienda activa y tener ADMIN o CAJERO. El nuevo backend rechaza cuentas PLATFORM_ADMIN en la API operativa. Tokens antiguos o revocados por suspensión/cambio de credenciales pueden exigir iniciar sesión de nuevo; Android respeta ese rechazo.

## Verificación

- **48 pruebas unitarias/de contrato** aprobadas: incluyen cargos manuales, respuesta antigua sin charges, identidad de refresh y rechazo de solicitudes de otra cuenta.
- **9 pruebas Android** aprobadas: configuración/login, POS, clientes, créditos, abonos, historial, permisos, cifrado y recuperación de intentos; incluye revocación de la cuenta A, inicio con B y rechazo del cliente de A sin usar el token de B.
- **4 pruebas del servidor** aprobadas en H2/JDK 21: aislamiento de tiendas y ventas de cajero; suspensión/reactivación sin resucitar tokens; inventario/caja y reversiones de crédito aislados; cargos idempotentes por cliente y tienda.
- Lint: **0 errores, 23 advertencias** existentes sobre versiones y configuración.
- APK debug y release sin firma generados con la configuración HTTPS habitual, sin excepción LAN del fixture.

Logs: `server-main-tests.log`, `server-main-openapi.log`, `server-compat-checks.log`, `server-compat-device.log` y `server-compat-package.log`.

## Compilar e instalar

Desde `E:/ERP-tienda/android-client`, con JAVA_HOME y ANDROID_HOME configurados:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

También se puede copiar `app/build/outputs/apk/debug/app-debug.apk` al teléfono y abrirlo para actualizar la instalación de desarrollo. La URL raíz configurada sigue siendo la misma; no se debe usar `/admin` ni introducir un tenantId en la URL.
