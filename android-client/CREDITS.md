# Créditos y Fiados — entrega Android

La revisión posterior del main multitienda y los cargos manuales está en [SERVER_COMPATIBILITY.md](SERVER_COMPATIBILITY.md). Las observaciones de producción y versión que siguen corresponden a la integración del 8 de octubre; la documentación pública ahora requiere login y el contrato actualizado se verificó desde main.

Integración del 8 de octubre de 2026. Mantiene Kotlin, Compose, ViewModel, Retrofit, coroutines, autenticación y dependencias existentes. No crea una base de datos financiera. El pago inmediato conserva el botón Cobrar directo en efectivo solicitado anteriormente.

## Pantallas disponibles

- **Créditos:** resumen y saldos del servidor en MXN, número de clientes con adeudo, búsqueda por nombre/apellido, filtros Pendientes/Liquidados/Todos, páginas, carga, errores, vacíos y actualización manual.
- **Clientes:** alta y edición para ADMIN, nombre obligatorio, apellido/teléfono/notas opcionales, advertencia de nombres parecidos, confirmación e identificador estable del servidor.
- **Estado de cuenta:** saldo, totales prestado/abonado, última actividad, resumen y movimientos cronológicos con referencia, cargo/abono y saldo posterior. Las ventas expanden productos con nombre, cantidad, precio y subtotal históricos enviados por el servidor.
- **Nuevo crédito:** reutiliza POS, búsqueda, CameraX/ML Kit, pitido, carrito y cantidades. Muestra existencias del producto, deuda anterior, total y saldo proyectado estimados. Confirma antes de enviar y muestra folio e importe confirmados. Una consulta posterior obtiene el saldo actualizado completo del cliente.
- **POS:** opción Crédito solo cuando el servidor declara la capacidad; selector y búsqueda de cliente, alta rápida para ADMIN, conservación del carrito al cambiar de modo, sin captura de efectivo ni cambio para fiados.
- **Abonos:** efectivo, transferencia o tarjeta; importe positivo, máximo dos decimales y no superior a deuda; confirmación con cliente e importes; saldo final confirmado por servidor. No se modifica inventario.
- **ADMIN:** cancelar crédito o revertir abono con motivo y confirmación. CAJERO no ve esas acciones. La API sigue siendo la autoridad final.
- **Ventas/tickets:** tipo de cobro, abonado y pendiente; el ticket compartido identifica el fiado. No reconstruye precios del catálogo actual.

## Contrato y backend requerido

Se inspeccionaron los controladores/modelos del backend actualizado en `E:/ERP-tienda-main-products-pagination`. Se generó su OpenAPI real ejecutando las clases de Spring con una BD H2 aislada: **backend-credits-openapi.json**. La utilidad `validation/GenerateBackendOpenApi.java` inicia un puerto local temporal y cierra el contexto al finalizar. No modifica el código del backend ni usa datos de producción.

Se descargó también el OpenAPI de producción desde `https://tienda.maus.lat/api-docs`, guardado en **backend-openapi.json**. En la comprobación del 8 de octubre no contenía clientes, créditos ni `settlementType` en checkout.

**Es necesario desplegar el backend que incorpora el módulo Créditos y la migración `V11__add_customer_credit_module.sql`, utilizando la secuencia de migraciones de esa versión.** Debe ofrecer los recursos siguientes y devolver `CREDIT` en `GET /api/v1/pos → settlementTypes`. El proyecto sigue declarando versión `0.1.0`; ese número por sí solo no distingue la capacidad. Android comprueba el contrato, no un número supuesto de versión.

Con el backend anterior, Créditos muestra el requisito de actualización y el POS conserva el cobro inmediato. Se bloquea un fiado antes del POST si falta CREDIT: nunca se envía como si fuera una venta en efectivo. No se modificó la API base, el inicio de sesión ni el backend desplegado.

## Endpoints utilizados

| Método | Ruta bajo `/api/v1` | Uso |
| --- | --- | --- |
| GET | `/pos` | Caja, métodos y capacidad CREDIT |
| GET | `/customers?q=&page=&size=` | Selector, búsqueda y similitud |
| GET | `/customers/{id}` | Datos para edición |
| POST | `/customers` | Alta, ADMIN |
| PUT | `/customers/{id}` | Edición, ADMIN |
| GET | `/credits/summary` | Totales del servidor |
| GET | `/credits/customers?q=&filter=&page=&size=&sort=` | Clientes paginados; DEBT, WITHOUT_DEBT, ALL |
| GET | `/credits/customers/{id}` | Estado de cuenta, movimientos y desglose histórico |
| POST | `/credits/customers/{id}/payments` | Abono con Idempotency-Key |
| POST | `/pos/checkout` | Venta con customerId y settlementType=CREDIT; Idempotency-Key |
| POST | `/credits/sales/{saleId}/cancel` | Cancelación con reason, ADMIN |
| POST | `/credits/payments/{paymentId}/reverse` | Reversión con reason, ADMIN |
| GET | `/products`, `/products/barcode/{barcode}` | Selección/escáner existentes |
| GET | `/sales`, `/tickets/{folio}` | Ventas/tickets existentes |

El checkout fiado envía discount=0, paymentMethod=CASH, receivedAmount=0 e items, según el contrato; `settlementType=CREDIT` hace que el backend no cree ingreso de efectivo. `pendingBalance` de la respuesta pertenece a esa venta, no a toda la deuda del cliente. La deuda total se obtiene del estado de cuenta.

El estado de cuenta ya incluye todos sus movimientos, por lo que no se duplica la consulta con `/movements` ni se inventa paginación de ese endpoint. Los saldos siempre vienen del servidor, sin sumar páginas cargadas para reconstruir deuda.

## Reintentos y permisos reales

Ventas y abonos generan un UUID una vez, conservan cliente/cuerpo exactos, bloquean doble pulsación y edición pendiente y reutilizan la clave tras timeout. El carrito se limpia y el resultado se muestra solo después de una respuesta válida. Un resultado incierto no genera otra operación. HTTP 400/422 permite corregir datos.

`EncryptedPendingOperationStore` guarda únicamente un checkpoint inmutable de reintento con AES/GCM y Android Keystore, en `noBackupFilesDir`, separado por servidor, usuario y flujo. Permite recuperar la operación incluso con un SavedStateHandle nuevo tras reiniciar. La E/S se ejecuta fuera del hilo principal. No guarda una deuda local confirmada ni forma una cola offline: exige reconexión y replay exacto. Si el registro no puede leerse, bloquea nuevos cobros/abonos en ese flujo en lugar de reemplazarlo por otra operación.

**Limitaciones del contrato:** altas/ediciones de clientes y reversiones no implementan replay por Idempotency-Key. No se afirma una garantía que el backend no ofrece. Un alta incierta bloquea repetición automática y permite consultar el listado. Las reversiones son transiciones de estado que el servidor valida y rechaza si ya se realizaron; se refresca el estado tras éxito y se puede actualizar tras error. Extender replay por clave a esos recursos requiere cambios del backend.

`ApiCustomerController` permite consultar a ADMIN y CAJERO, pero su POST/PUT exige `hasRole('ADMIN')`. Android respeta esa restricción: alta/edición y alta rápida aparecen solo para ADMIN. Para permitir altas a CAJERO se debe ampliar ese permiso en el backend. CAJERO sí consulta cuentas, registra fiados y abonos. No se ampliaron permisos en Android para eludir el servidor.

## Archivos creados y modificados

Rutas relativas a `E:/ERP-tienda/android-client`.

**Creados:**

- `app/src/main/java/com/mauspancho/tiendapos/data/dto/CreditModels.kt`: clientes/solicitudes, resumen, cuentas, ventas/líneas históricas, movimientos, abonos y checkpoints.
- `app/src/main/java/com/mauspancho/tiendapos/domain/repository/CreditRepository.kt`.
- `app/src/main/java/com/mauspancho/tiendapos/data/repository/RemoteCreditRepository.kt`.
- `app/src/main/java/com/mauspancho/tiendapos/data/storage/PendingOperationStore.kt`.
- `app/src/main/java/com/mauspancho/tiendapos/ui/credits/CreditsViewModel.kt` y `CustomerPickerViewModel.kt`.
- `app/src/main/java/com/mauspancho/tiendapos/ui/credits/CreditsScreen.kt` y `CustomerPicker.kt`.
- `app/src/test/java/com/mauspancho/tiendapos/CreditsTest.kt` y `CreditNetworkTest.kt`.
- `app/src/androidTest/java/com/mauspancho/tiendapos/CreditFlowDeviceTest.kt`, `CreditPermissionUiTest.kt` y `PendingStoreDeviceTest.kt`.
- `validation/GenerateBackendOpenApi.java`, `backend-openapi.json`, `backend-credits-openapi.json` y `CREDITS.md`.

**Modificados:** `MainActivity.kt`, `navigation/Destination.kt`, `data/api/TiendaApi.kt`, `data/dto/Models.kt`, `ui/pos/PosViewModel.kt`, `ui/pos/PosScreen.kt`, `ui/components/Components.kt`, `data/storage/MediaStorage.kt`, `.gitignore`, `README.md` y `VALIDATION.md`.

## Generar e instalar APK

Desde `E:/ERP-tienda/android-client`, PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\mausp\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME='E:\ERP-tienda\android-client\.gradle-user'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

También se puede copiar `app/build/outputs/apk/debug/app-debug.apk` al teléfono y abrirlo para actualizar la instalación de desarrollo. Android mínimo: 8.0/API 26. Release no incluye firma de distribución ni claves nuevas.

Las pruebas con fixture dentro del emulador usan exclusivamente `'-PlanHost=10.0.2.15'`. El APK final se reconstruye sin esa propiedad, con HTTPS normal.

## Validación

La prueba existente `ApiV1IntegrationTest#creditLifecycleKeepsInventoryCashBalancesIdempotencyAndPermissionsConsistent` pasó con H2 aislado. Comprueba el módulo REST y las vistas web sobre los mismos registros, descuento de stock por fiado, ausencia de ingreso de efectivo por ese fiado, abonos sin mover stock, replay, permisos de reversión y saldo final.

Las pruebas Android recorren alta sin apellido/teléfono, fiado, abono que liquida, historial con precios/nombres históricos, cobro inmediato existente, sesión, permisos de CAJERO y cifrado/aislamiento/manipulación de checkpoints. Las unitarias/de contrato comprueban montos inválidos, servidor anterior, solicitudes HTTP, doble pulsación, timeout y recuperación de la misma clave/cuerpo con SavedStateHandle nuevo.

Los resultados finales están en `VALIDATION.md` y los logs `credits-verification.log`, `credits-package.log`, `credits-backend-test.log` y `credits-openapi-generation.log`.

No se registraron deudas o abonos reales en producción. La validación de la tienda real depende de desplegar el backend actualizado; su OpenAPI inspeccionado todavía no publica estas funciones. No hay una base de datos financiera Android ni créditos offline.
