# Validación y resumen de entrega

Ejecución local: 1 de octubre de 2026, zona America/Mexico_City. Cliente en `E:\ERP-tienda\android-client`. Backend inspeccionado en `E:\ERP-tienda-main-products-pagination`; no se modificó ni desplegó el backend y no se hizo commit/push.

## Resultado

| Comprobación | Resultado |
| --- | --- |
| `gradlew.bat test` | Correcto: 30 pruebas unitarias, 0 fallos |
| `gradlew.bat assembleDebug` | Correcto; APK debug generado |
| `gradlew.bat lintDebug` | Correcto, 0 errores y 23 advertencias de versiones disponibles y recomendaciones de configuración/recursos |
| `gradlew.bat connectedDebugAndroidTest '-PlanHost=10.0.2.15'` | Correcto: 4 pruebas instrumentadas, 0 fallos, 0 omitidas en Android 17/API 37 |
| `gradlew.bat assembleRelease` | Correcto, R8 habilitado; APK release **sin firma de distribución** |
| Inicio real del APK debug en emulador | Correcto; muestra Configurar servidor y bloquea Guardar hasta validar health |

Las pruebas instrumentadas usan la excepción HTTP **para la dirección privada concreta del propio emulador** y un servidor MockWebServer desechable. El APK entregado se compila posteriormente sin `lanHost`, por lo que usa HTTPS obligatorio. Para LAN hay que compilar con la excepción del host deseado, según README.

APK debug exacto:

`E:\ERP-tienda\android-client\app\build\outputs\apk\debug\app-debug.apk`

APK release sin firma:

`E:\ERP-tienda\android-client\app\build\outputs\apk\release\app-release-unsigned.apk`

Informes:

- Unitarias: `app\build\reports\tests\testDebugUnitTest\index.html`.
- Instrumentadas: `app\build\reports\androidTests\connected\debug\index.html`.
- Lint: `app\build\reports\lint-results-debug.html`.
- Captura de inicio: `docs\screenshots\startup.png`.

## Cobertura concreta

**Arquitectura:** Compose → ViewModel/StateFlow → repositorio → Retrofit/OkHttp → API v1. Separación de DTO, almacenamiento, modelos y navegación. Inyección explícita por constructor, sin Hilt ni un segundo sistema de DI.

**Pantallas:** configurar servidor, login, Inicio, análisis de ganancias, Productos y formulario/detalle/imagen/promociones, POS y cobro/resultado, Compras, Inventario y existencias, Ventas, Tickets, Caja e histórico, Gastos, Finanzas e histórico/detalle/capital, Reportes, Categorías, Proveedores, Usuarios, Configuración móvil y datos de tienda.

**Dependencias:** Kotlin/Compose/Material 3, Lifecycle, Navigation, DataStore, Paging 3, Coroutines, kotlinx.serialization, Retrofit, OkHttp, Coil, CameraX y ML Kit; versiones exactas y herramientas en README.

**Endpoints:** tabla completa en README; únicamente rutas verificadas bajo `/api/v1/**`. Listas paginadas y filtros según el contrato. Detalle de existencias usa `products/{id}`; capital usa su respuesta de listado porque no existe un endpoint individual de detalle. No se inventaron rutas de cancelación, impresión, PDF o cambio de contraseña.

**Autenticación:** contrato exacto de login/refresh/logout/me, Bearer, refresh sincronizado, un reintento, limpieza de sesión inválida y aislamiento de servidor. Los tests comprueban renovación concurrente única, errores transitorios, refresh inválido, segundo 401 y respuestas tardías de una sesión anterior.

**Tokens:** AtomicFile cifrado con AES/GCM y Android Keystore, IV aleatorio, sin passwords ni tokens en preferencias normales, backup y transferencia de datos deshabilitados. Prueba instrumentada real de cifrado/restauración y rechazo de manipulación.

**Roles:** ADMIN/CAJERO y roles exactos recibidos. Menú por capacidades de la API; CAJERO tiene Caja en el contrato REST. 403 mantiene el control del servidor y muestra un mensaje claro. Roles desconocidos no obtienen permisos automáticamente.

**Scanner:** CameraX + ML Kit con EAN-13/EAN-8/UPC-A/UPC-E/Code 128, control de duplicados, escaneo continuo y contador visual. Consulta barcode real y ofrece alta manual a ADMIN. Permiso de cámara al usarla. Pendiente probar códigos físicos y captura con hardware real.

**POS:** carrito, búsqueda remota, cantidades enteras/fraccionarias, incremento/disminución/quitar, BigDecimal exacto, métodos del servidor, caja abierta, venta idempotente, bloqueo de duplicados y de edición de una venta incierta, conservación de UUID/cuerpo, limpieza solo tras respuesta válida, resultado y ticket nativo. El flujo instrumentado completo pasó con transporte real hacia el fixture: login → Inicio → POS → buscar/agregar → cobrar → ticket → logout.

**Conexión:** carga, vacíos, errores, reintento, DNS/SSL/timeout/HTTP/incompatibilidad, sin stack traces. Carrito no confirmado conservado en rotación y restauración normal por Android. HTTPS por defecto; excepción LAN explícita por host, sin bypass de certificados.

## Límites de esta validación

- No se usaron credenciales reales ni se probaron ventas/compras/ajustes contra una tienda de producción. Los fixtures reproducen DTO y rutas de la copia de backend inspeccionada. La validación con una instalación real autorizada sigue pendiente.
- El backend original en `E:\ERP-tienda` no expone esta API v1. Se necesita ejecutar una versión compatible del backend inspeccionado para operar.
- Falta soporte de API para cancelaciones, ticket PDF, cambios de contraseña dedicados, edición/eliminación de gastos, movimientos manuales de caja y filtros adicionales de ventas.
- El PUT/multipart de producto exige stock. El cliente lo recupera inmediatamente antes de guardar, pero no puede garantizar atomicidad GET+PUT. Un PATCH que ignore stock o versiones/If-Match es necesario para garantizar edición concurrente segura. Se documenta como limitación real del servidor, no se oculta ni se modificó el backend.
- Gráficas, logo por multipart, filtros adicionales y vibración son mejoras pendientes; los datos financieros se presentan nativamente sin duplicar fórmulas.
- Carrito/venta pendiente no son una cola offline duradera: SavedStateHandle no garantiza recuperación tras force-stop o eliminación de la tarea. No hay venta local confirmada ni sincronización offline.
- Release compila pero no se entrega firmado para publicación. No se inventaron claves ni secrets.

## Corrección del POS — 1 de octubre de 2026

- El campo Cantidad se sincroniza con la cantidad del carrito después de incrementar, disminuir o escanear otra unidad. Aplicar conserva el valor actualizado y permite cantidades fraccionarias.
- Un pitido corto confirma cada adición realizada por el escáner mientras la cámara está visible y la app activa. Los fotogramas duplicados, consultas fallidas y bloqueos por cobro pendiente no generan confirmación. Se usa el volumen multimedia del dispositivo y se liberan los recursos de audio al cerrar el escáner.
- Validación: 31 pruebas unitarias aprobadas; 3 pruebas de interfaz aprobadas en el emulador, incluida la regresión de +, −, Aplicar y cantidades fraccionarias; APK debug compilado; lint sin errores. Logs: `pos-fix-checks.log` y `pos-fix-device.log`.
- La salida de sonido y el escaneo con cámara física quedan pendientes de comprobación en un teléfono real; el emulador se ejecutó sin audio.

## Cobro directo en efectivo — 2 de octubre de 2026

- El POS tiene un botón Cobrar que envía la venta directamente en efectivo. Se eliminaron el selector de método, el campo de importe recibido y la confirmación adicional.
- La API exige `receivedAmount`: se envía automáticamente el total del carrito, con redondeo HALF_UP por línea a dos decimales, conforme a SaleService/MoneyUtils del backend inspeccionado. No se modificó el backend.
- Se conservan la caja abierta obligatoria, el bloqueo de dobles pulsaciones, el carrito hasta recibir respuesta válida y el mismo UUID/cuerpo para reintentar una venta incierta. Los cobros pendientes guardados por versiones anteriores conservan su cuerpo original para evitar duplicarlos.
- Pasaron 32 pruebas unitarias y 4 pruebas de Android, incluido el flujo con transporte real hacia un fixture aislado: login, agregar, Cobrar con una sola pulsación, ticket y logout. La solicitud comprobada fue CASH y recibió automáticamente 12.30 para un carrito de 12.30. Lint sin errores. Logs: `cash-direct-checks.log`, `cash-direct-device.log`, `cash-direct-apk.log`.

## Limpieza del buscador del POS — 5 de octubre de 2026

Al seleccionar un resultado y agregarlo al carrito, se vacían el texto y los resultados del buscador. Se cancela la consulta anterior para permitir la siguiente búsqueda. Si la adición está bloqueada por un cobro pendiente, no se limpia el campo. Pasaron las 32 pruebas unitarias existentes y el flujo instrumentado de Android, que comprueba el campo vacío tras seleccionar y la entrada de otra búsqueda. Logs: `search-clear-checks.log`, `search-clear-device.log`, `search-clear-apk.log`.

## Créditos y Fiados — 8 de octubre de 2026

Se integraron Créditos, selección/alta/edición de clientes según permisos del servidor, cuenta e historial con precios históricos, fiados desde POS y desde Créditos, abonos con confirmación, reversiones administrativas y tickets a crédito. El cobro inmediato sigue siendo directo en efectivo. Contrato, archivos, clases, endpoints, generación e instalación: `CREDITS.md`.

Validación: 44 pruebas unitarias/de contrato aprobadas, 8 pruebas en Android aprobadas, prueba de ciclo de crédito del backend aprobada en H2 aislado y OpenAPI actualizado generado por Springdoc desde el código inspeccionado. El flujo Android usa Retrofit real contra un fixture del emulador. Se comprobaron cliente sin apellido/teléfono, claves y cuerpos, fiado/abono/estado de cuenta, historial independiente del catálogo, permisos de CAJERO, cifrado y recuperación del checkpoint, y pago inmediato. Lint: 0 errores y 23 advertencias, principalmente versiones de dependencias y configuraciones existentes. Debug y release sin firma compilaron.

El checkpoint de cobros/abonos está cifrado y aislado por servidor/usuario/flujo, y conserva la solicitud exacta incluso con SavedStateHandle nuevo. No crea créditos offline ni saldos locales. Las pruebas cubren recuperación, fallo de lectura y bloqueo de nuevas escrituras ante un registro ilegible.

La API de producción comprobada en `/api-docs` todavía no publica Créditos, clientes ni settlementType. Android bloquea el fiado si `/api/v1/pos` no declara CREDIT. Se requiere desplegar el backend actualizado con el módulo y migración de créditos. No se realizaron operaciones reales en producción.

Diferencias obligadas por el contrato: POST/PUT de clientes exige ADMIN; CAJERO no puede registrar/editar clientes hasta ampliar permisos en el backend. Altas de clientes y reversiones no ofrecen replay por clave; no se inventó ese soporte. El alta incierta bloquea repetición automática y las reversiones se validan como cambios de estado en servidor.

Logs: `credits-verification.log`, `credits-package.log`, `credits-backend-test.log`, `credits-openapi-generation.log` y `credits-checkpoint-check.log`. OpenAPI: `backend-openapi.json` (producción) y `backend-credits-openapi.json` (backend actualizado aislado).

## Compatibilidad con main multitienda — 13 de octubre de 2026

Main verificado contra GitHub: `adf13191538c2372b1389dcfb1325340832a86e6`. OpenAPI generado desde ese código, en Spring/H2 aislado, guardado como `backend-main-openapi.json`. Las rutas/modelos anteriores se conservan; StatementResponse añade cargos manuales y existen dos endpoints adicionales de cargos. Android ahora lee y muestra esos cargos sin calcular una deuda independiente.

La identidad de la solicitud se mantiene en una etiqueta interna de OkHttp; no se envía un tenantId/header de tienda. Se rechazan renovaciones entre cuentas y el cliente anterior se cierra/cancela al revocar sesión antes de volver al login. El siguiente usuario utiliza un cliente nuevo. La prueba de dispositivo verifica revocar A, entrar como B y que una petición tardía de A no envíe credenciales de B.

Pasaron 48 pruebas unitarias, 9 pruebas Android y 4 pruebas de servidor en H2 con JDK 21. Lint: 0 errores, 23 advertencias. Detalle de cambios, rutas, requisitos, archivos y pruebas: `SERVER_COMPATIBILITY.md`. No se cambió código del servidor ni se realizaron cargos/abonos de producción. La documentación de producción actualmente devuelve el login al visitante anónimo; no se afirma que ese HTML sea el contrato OpenAPI.

No se afirma equivalencia total con todas las funciones web; el alcance implementado y los límites están descritos arriba y en README.
