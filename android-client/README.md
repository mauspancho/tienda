# Tienda POS — cliente Android nativo

Actualización del 14 de octubre de 2026: main `0bf25e8` integrado y módulo nativo de [WhatsApp y promociones](WHATSAPP.md). Para habilitarlo hay que desplegar también los nuevos adaptadores JWT del backend incluidos en la rama `android`.

Compatibilidad revisada con el main multitienda del 13 de octubre de 2026: [SERVER_COMPATIBILITY.md](SERVER_COMPATIBILITY.md). Incluye soporte de lectura de cargos manuales y aislamiento de solicitudes al cambiar de cuenta.

La integración de **Créditos y Fiados** está documentada en [CREDITS.md](CREDITS.md): pantallas, endpoints comprobados, permisos reales, checkpoints cifrados, requisitos del backend, pruebas e instalación. El servidor de producción debe actualizarse para habilitarla; el cliente conserva el cobro directo en efectivo en servidores anteriores.

Proyecto Android independiente: Kotlin, Jetpack Compose y Material 3, package `com.mauspancho.tiendapos`. No contiene Spring Boot, HTML, WebView ni conectores a bases de datos. El servidor y su base de datos siguen siendo la fuente de verdad.

## Contrato inspeccionado

En la creación inicial se inspeccionó la API en `E:\ERP-tienda-main-products-pagination\src\main\java\com\tienda\pos\api\v1`. La revisión actual utiliza el main multitienda del servidor, que conserva `/api/v1/**`; el resultado está en `SERVER_COMPATIBILITY.md`. La actualización de WhatsApp añade los adaptadores JWT documentados en `WHATSAPP.md`.

El cliente se creó como proyecto autocontenido en `android-client/` y se incorpora al repositorio `mauspancho/tienda` en la rama `android`. Puede compilarse sin el proyecto Maven. El wrapper Gradle es oficial y verifica el SHA-256 de la distribución. Las cachés, claves, archivos de compilación y `local.properties` no se publican.

OpenAPI del servidor: `/api-docs`; Swagger: `/swagger-ui`. Son documentación del servidor, no rutas que el cliente necesita para operar. En esta ejecución se usaron las fuentes locales como referencia; no se contó con una instalación real autenticada para comprobar operaciones de negocio.

## Herramientas y versiones

| Herramienta | Versión fijada |
| --- | --- |
| Android Gradle Plugin | 9.1.1 |
| Gradle wrapper | 9.3.1 |
| Kotlin integrado de AGP / plugins Compose y Serialization | 2.2.10 |
| Compose BOM | 2025.10.01 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Build Tools | 36.0.0 |
| Nivel de bytecode Java | 17 |

Estas versiones fueron resueltas y compiladas en el entorno. Android Studio instalado: build `AI-262.9437.185.2621.16467767`; JBR 25.0.3 incluido; plataforma `android-37.0` instalada. AGP 9.1.1 admite API 37 y requiere Gradle 9.3.1 como mínimo ([compatibilidad oficial](https://developer.android.com/build/releases/agp-9-1-0-release-notes)). No se usan versiones dinámicas, alpha, beta ni actualizaciones automáticas.

Dependencias principales: Activity 1.11.0; Lifecycle 2.9.4; Navigation Compose 2.9.5; DataStore 1.1.7; Paging 3.3.6; Coroutines 1.10.2; Serialization JSON 1.9.0; Retrofit y su converter oficial 3.0.0; OkHttp 4.12.0; Coil 3.3.0; CameraX 1.5.0; ML Kit Barcode Scanning 17.3.0. JUnit, Coroutines Test y MockWebServer para unitarias; Compose UI Test, AndroidX Test y Espresso 3.7.0 para instrumentadas. Espresso se fija explícitamente porque una dependencia transitiva anterior usaba una API eliminada en Android 17.

## Abrir, compilar y ejecutar

Abrir **esta carpeta** en Android Studio, permitir la sincronización Gradle y seleccionar un emulador o dispositivo Android 8.0 o superior. Configurar el SDK en Android Studio o mediante `ANDROID_HOME`. No subir `local.properties`, claves de firma ni credenciales al repositorio.

PowerShell en esta carpeta:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Linux/macOS: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew lintDebug` y `./gradlew connectedDebugAndroidTest`, con SDK 37 y Java compatible configurados. Si se copió el proyecto desde Windows puede ser necesario `chmod +x gradlew`.

APK debug: `app\build\outputs\apk\debug\app-debug.apk`. Se puede instalar desde Android Studio o con `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Servidor, HTTPS y LAN

Primer inicio: **Configurar servidor → Probar conexión → Guardar y continuar → Login**. No se guarda una URL sin verificar que health responda `status=UP`, `application=Tienda POS`, `apiVersion=v1` y `serverVersion` no vacía. Editar la URL invalida la prueba anterior. Se permiten prefijos de contexto, por ejemplo `https://ejemplo.com/tienda/`; se rechazan credenciales embebidas, espacios internos, query, fragmentos y URLs que incluyan `/api/v1`.

Ejemplo HTTPS: `https://tienda.ejemplo.com`. Es la configuración normal del APK, sin excepciones HTTP. No se aceptan certificados inválidos ni se instalan trust managers permisivos.

Para una LAN HTTP, habilitar **una dirección IPv4 privada concreta** al compilar y después ingresarla en la pantalla. El valor solo es una excepción de transporte: no configura automáticamente el servidor, puerto ni credenciales.

```powershell
.\gradlew.bat assembleDebug '-PlanHost=192.168.1.146'
# Ingresar en la app: http://192.168.1.146:8092
```

La política Android generada deniega HTTP por defecto y permite únicamente ese host. OkHttp aplica además la misma restricción; no se siguen redirecciones y un token solo puede acompañar peticiones a `/api/v1/**` del origen configurado. Coil utiliza un cliente sin credenciales y con la misma política de transporte. Nunca hay un `base-config` que habilite HTTP globalmente.

En el emulador estándar, `localhost` es el emulador. Para el backend del PC, compilar con `'-PlanHost=10.0.2.2'` y escribir `http://10.0.2.2:8080` u otro puerto real. En un teléfono usar la IP LAN del PC; revisar conectividad, firewall y el puerto que escucha el servidor. Las direcciones de estos ejemplos no quedan guardadas como servidor de producción. Volver a compilar **sin** `lanHost` restaura HTTPS obligatorio. Release también usa HTTPS por defecto; una excepción LAN requiere el mismo argumento explícito.

## Arquitectura

`data/api`: Retrofit, OkHttp, parser de errores y refresh. `data/dto`: contratos JSON, con decimales exactos BigDecimal. `data/repository`: implementaciones remotas y PagingSources. `data/storage`: DataStore, Keystore y archivos privados compartidos con FileProvider. `domain/model`: URL, carrito, intento de cobro y contratos de formularios. `domain/repository`: interfaces de sesión, servidor, productos y POS. `ui/*`: ViewModels con StateFlow y Compose. `navigation`: destinos y roles.

Los Composables observan estado y emiten acciones. No ejecutan llamadas Retrofit. La composición de dependencias es explícita por constructor en el punto de entrada; no se añadió Hilt/KAPT/KSP al proyecto nuevo, evitando un segundo sistema de DI y procesadores extra. Los repositorios y ViewModels críticos se prueban con implementaciones de sus interfaces. Los módulos administrativos comparten componentes nativos de formularios, búsqueda de referencias, detalles y paginación; sus campos y rutas están definidos explícitamente a partir de la API inspeccionada. Campos desconocidos de respuestas no aparecen automáticamente en pantalla.

Navegación: barra inferior para Inicio/POS/Productos, drawer para el resto y rail desde 600 dp. Tema claro, oscuro o del sistema. Listas LazyColumn, Paging 3, estados de carga, vacío, error y reintento. Dashboard tiene pull-to-refresh. Formularios conservan campos no sensibles al recrear actividad; las contraseñas viven exclusivamente en memoria.

## Autenticación y almacenamiento

Login envía exactamente `{username,password}`. El servidor devuelve `{accessToken,refreshToken,tokenType,expiresIn,user}`. Se consulta `/auth/me`; los roles y el usuario vienen de la API. Se usa `Authorization: Bearer …`.

Tokens y usuario se almacenan juntos mediante AES-256/GCM con clave no exportable de Android Keystore, IV aleatorio y AAD, en un archivo AtomicFile de `noBackupFilesDir`. La sesión queda ligada a la URL del servidor. No se guardan contraseñas, tokens en DataStore/SharedPreferences ni encabezados de autorización en logs. Backup deshabilitado. Una sesión cifrada dañada no se acepta.

Ante 401, un autenticador sincronizado renueva una sola vez. Las peticiones concurrentes reutilizan el token renovado. 400/401/403 de refresh eliminan credenciales; errores transitorios de red o 5xx conservan la sesión para reintentar. Logout borra primero datos locales e intenta revocar refresh en el servidor. Cambiar servidor cierra la sesión, cancela peticiones, elimina datos asociados y valida la nueva URL antes de persistirla. Una sesión cerrada no puede guardar un refresh que llegue tarde. Los ViewModels del usuario anterior dejan de usarse.

URLs y tema se guardan en DataStore. Carrito e intento de cobro se conservan en SavedStateHandle para rotaciones y restauración normal del proceso por Android, ligados al ámbito de sesión. No hay ventas offline ni sincronización bidireccional. SavedStateHandle **no garantiza** conservar el carrito tras force-stop, eliminación de la tarea o reinicio del teléfono; tampoco se afirma que una venta sin confirmación se realizó.

## Funcionalidad y endpoints

Todas las rutas siguientes se resuelven bajo la URL configurada y `/api/v1/`.

| Pantalla | Endpoints y operaciones |
| --- | --- |
| Servidor | `GET health` |
| Cuenta | `POST auth/login`, `POST auth/refresh`, `POST auth/logout`, `GET auth/me` |
| Inicio | `GET dashboard`, `GET dashboard/profit` por fecha y periodo día/semana/mes; indicadores calculados en servidor, productos destacados y ventas diarias |
| Productos | `GET products` paginado, filtros q/brand/active; detalle `GET products/{id}`; JSON `POST products`, `PUT products/{id}`; multipart `PUT products/{id}` partes `product` e `image`; `PATCH …/active`, `…/promotion`, `…/whatsapp-promotion` con `{enabled}` |
| POS | `GET pos` para caja y métodos reales; `GET products/barcode/{barcode}`; búsquedas paginadas; `POST pos/checkout` con `Idempotency-Key` |
| Compras | `GET purchases` paginado y con filtros de fecha/fuente, `GET purchases/{id}`, `POST purchases` con idempotencia; proveedor, producto, cantidades, costo, folio, fuente de dinero y actualización de costo |
| Inventario | `GET inventory/stock`, `GET inventory/movements`, `GET inventory/movements/{id}`, `POST inventory/adjustments`, `POST inventory/movements/{id}/reverse` cuando `reversible` |
| Ventas y tickets | `GET sales`, `GET sales/{folio}`, `GET tickets/{folio}`; ticket nativo compartible como texto con datos del servidor |
| Caja | `GET cash/current`, `POST cash/open`, `POST cash/{id}/close`; ADMIN: `GET cash/sessions`, `GET cash/sessions/{id}` con movimientos |
| Gastos | `GET expenses`, `GET expenses/{id}`, `GET expenses/categories`, `POST expenses` |
| Finanzas | `GET finances`, `GET finances/daily`, `GET finances/daily/detail?date=…`, `GET finances/capital`, `POST finances/capital`, `GET finances/daily.csv`; resumen, detalle diario, rentabilidad por producto y series mensuales del servidor, sin fórmulas financieras locales |
| Reportes | `GET reports?period=TODAY/WEEK/MONTH`; datos nativos calculados por el servidor |
| Categorías | Listado/búsqueda paginados, detalle, POST/PUT y PATCH active bajo `categories` |
| Proveedores | Listado/búsqueda paginados, detalle, POST/PUT y PATCH active bajo `suppliers` |
| Usuarios | Listado paginado, detalle, POST/PUT, PATCH active y DELETE bajo `users`; no se muestran hashes ni tokens; no se cambia contraseña mediante PUT |
| Datos de tienda | `GET settings`, `PUT settings` JSON; información de negocio y configuración expuesta por el servidor |
| Configuración móvil | Servidor, cuenta, tema y acerca de |

Las búsquedas de productos y referencias cargan páginas de 20 registros; no descargan todo el catálogo. Selector de producto para compras usa `products/search`. Inventario se modifica mediante operaciones del servidor; el formulario de edición de producto no ofrece editar stock. Alta permite stock inicial porque el Service registra el movimiento correspondiente.

ADMIN accede a los módulos administrativos. CAJERO puede acceder a Dashboard, POS, Productos, Ventas, Tickets y **su Caja**, según los permisos reales de API, que difieren de los permisos de algunos controladores web. Roles desconocidos no adquieren permisos por su nombre de usuario. 403 siempre muestra un mensaje de falta de permisos; el servidor mantiene la autoridad final.

Scanner: CameraX y ML Kit local, EAN-13, EAN-8, UPC-A, UPC-E y Code 128; permiso solicitado al abrir cámara; análisis de un frame a la vez, control de duplicados de 1.2 s y consultas en vuelo serializadas. Escaneo continuo incrementa la cantidad del producto encontrado; el contador/total ofrece respuesta visual. Si no existe, ADMIN puede abrir el formulario con barcode prellenado; jamás se crea automáticamente. Galería usa Photo Picker; captura usa cámara y FileProvider; tamaño máximo de imagen 5 MB, de acuerdo con el backend.

Carrito: búsqueda, escaneo, agregar, incrementar, disminuir, quitar, precio, subtotal y total estimado BigDecimal. El botón Cobrar registra directamente el pago en efectivo (`CASH`), sin selector, captura de importe ni confirmación adicional. El cliente envía automáticamente el total como `receivedAmount`, redondeando cada línea a centavos como el backend. Exige caja abierta y soporte de efectivo en `GET pos`, bloquea dobles envíos y conserva UUID **y cuerpo idénticos** al reintentar una operación incierta. Durante ese estado no permite cambiar el carrito. Se limpia exclusivamente al recibir una venta válida del servidor. No hay un endpoint de consulta por idempotency key: se usa el replay del POST real. El resultado muestra folio, fecha, total, cambio y ticket.

Conexión: mensajes diferenciados para DNS, timeout, SSL, servidor inaccesible, respuesta inválida, API incompatible y HTTP. Se procesan `fieldErrors`; no se presentan JSON crudo, stack traces ni nombres de excepciones Java. Las cancelaciones de coroutines se propagan. No hay reintentos automáticos de mutaciones por transporte.

## Pruebas

Unitarias: normalización y seguridad de URL, health compatible, imágenes, sesión ligada a servidor, logout, refresh concurrente e inválido, refresh transitorio, límites de reintento, autenticación ViewModel, errores HTTP, petición real Retrofit a MockWebServer, paginación, roles, carrito, cantidades, precisión decimal, conservación del intento y su UUID, recreación del ViewModel y cambios/cancelación de prueba de servidor.

Instrumentadas: servidor probado antes de guardar, invalidación al editar URL, login y roles, cifrado real Keystore y rechazo de manipulación. Prueba de flujo completo con Activity/DataStore/Keystore/Retrofit: login → Inicio → POS → búsqueda → carrito → cobro → ticket → configuración → logout. El servidor es un MockWebServer desechable dentro del emulador; no se crean operaciones en una tienda real.

Para ejecutar también la prueba completa en el emulador instalado `Medium_Phone_API_37.0`:

```powershell
.\gradlew.bat test lintDebug connectedDebugAndroidTest '-PlanHost=10.0.2.15'
# 10.0.2.15 es aquí el propio emulador, para el fixture de la prueba.
# Para un backend real en el PC, usar 10.0.2.2 como se explicó antes.
.\gradlew.bat assembleDebug
# El último comando elimina la excepción LAN y genera el APK normal HTTPS.
```

La prueba completa se omite si se ejecuta sin su excepción LAN explícita. No usarla contra un dispositivo con datos reales: se ejecuta en una instalación de pruebas del emulador y limpia la configuración de esa instalación al finalizar. No se ha validado lectura de códigos físicos ni captura de cámara real en este entorno.

Resultados de la ejecución y pendientes detallados: [VALIDATION.md](VALIDATION.md).

## Límites reales y pendientes de equivalencia

1. El backend en `E:\ERP-tienda` debe ejecutar una versión que exponga API v1; la versión inicial en esa carpeta no sirve para este cliente. No se modificó ni desplegó el servidor.
2. Falta validación de extremo a extremo contra una instalación real con usuarios autorizados y datos de negocio; las pruebas de transporte usan contratos inspeccionados y fixtures.
3. La API no expone cancelación de venta, PDF de ticket, impresión Bluetooth, edición/eliminación de gastos, movimientos manuales de caja, ni cambio dedicado de contraseña. No se inventaron rutas para esas operaciones.
4. La lista de ventas solo soporta page/size en el controlador revisado; no se añadieron filtros ficticios de fecha, folio, estado o usuario. Compras sí acepta fechas y fuente, y ambos filtros están disponibles en la UI.
5. **Concurrencia al editar productos:** el PUT real exige `currentStock` y el Service permite cambiarlo. El cliente oculta ese campo al editar y recupera el stock vigente inmediatamente antes de PUT/multipart; aun así, GET+PUT no es atómico y una venta concurrente puede ocurrir entre ambos. Para garantizar que editar metadatos o imagen jamás cambie stock se necesita un PATCH específico que ignore stock o soporte de versiones/If-Match en el backend. No se afirma que el cliente pueda resolver esa limitación del servidor.
6. La respuesta UserResponse no trae firstName/lastName separados; para editar usuario se pide volver a ingresar ambos, sin inferirlos dividiendo displayName. Cambiar contraseña no se habilita porque falta el endpoint específico requerido.
7. El cliente muestra resumen financiero, análisis de ganancias, histórico y detalle diario, capital y rentabilidad por producto. Las series se muestran como datos nativos; las gráficas visuales quedan como mejora opcional. No se duplicaron sus cálculos.
8. Logo de tienda por multipart, selección más amplia de filtros de producto/movimientos y vibración opcional del scanner son mejoras pendientes. POS admite incremento por unidades y captura explícita de cantidades fraccionarias BigDecimal.
9. Release no contiene logging HTTP; no tiene una clave de firma de producción. La entrega es APK debug. Firmar y distribuir requiere claves del propietario.

Este cliente cubre los módulos y operaciones descritos arriba; no se presenta como equivalencia total ya verificada con toda la web.
