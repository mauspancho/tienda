# Actualización de Android con main — 14 de octubre de 2026

Se integró `origin/main` hasta `0bf25e8` en la rama `android`. Los cambios principales del servidor son WPPConnect, anuncios multitienda, promociones manuales a grupos o números y sincronización asíncrona de grupos. Las pantallas nativas usan los mismos servicios de negocio que la web.

## Uso en Android

- Menú **WhatsApp** para ADMIN de tienda: consultar conexión, conectar/reconectar, mostrar QR, actualizar estado y desvincular la asociación de la tienda con confirmación.
- Sincronizar grupos con progreso; seleccionar grupos por identificador, guardar y habilitar/deshabilitar anuncios con confirmación.
- Productos: filtro paginado de promociones WhatsApp; detalle → **Enviar promoción por WhatsApp**. Vista previa del servidor, mensaje editable, grupos o número individual, revisión y confirmación explícita.
- Resultado por destinatario y cantidades aceptadas/fallidas. Aceptado por el proveedor no significa entregado o leído.
- Si la respuesta se pierde, se guarda un checkpoint cifrado separado por servidor y usuario. El reintento conserva producto, mensaje, destinatario y `Idempotency-Key`; un resultado parcial no se reenvía automáticamente.

CAJERO no puede entrar al módulo ni ejecutar sus endpoints. La cuenta PLATFORM_ADMIN administra plataforma, proveedor, secretos y automatizaciones desde la web; Android explica esta restricción al iniciar sesión. No se amplían los permisos de esa cuenta.

El QR permanece en memoria, con límites de tamaño y dimensiones; se elimina de la pantalla al salir o al quedar conectado. Las consultas periódicas se ejecutan mientras la pantalla está activa. Android no almacena credenciales de WPPConnect ni tokens de integración/n8n.

## Actualización necesaria del servidor

**Instalar solamente el APK no habilita WhatsApp móvil.** Main incorporó rutas web con sesión/CSRF y APIs con secretos de integración, pero no tenía rutas JWT para este módulo. Esta rama añade `src/main/java/com/tienda/pos/api/v1/whatsapp/` y sus pruebas. Hay que desplegar también el backend de esta rama (o integrar esos adaptadores en main) y configurar WPPConnect desde la plataforma.

Los adaptadores usan el JWT existente y `hasRole('ADMIN')`; la tienda se resuelve con `CurrentTenant`. No se devuelven entidades completas del proveedor ni secretos. Los envíos conservan la idempotencia persistente y los controles de stock, imagen, destinatarios y tienda del servicio existente.

| Método | Ruta bajo `/api/v1/whatsapp` | Función |
| --- | --- | --- |
| GET | raíz | Conexión, anuncios, grupos y estado de sincronización |
| POST | `/connect`, `/reconnect`, `/refresh`, `/disassociate` | Acciones de conexión |
| GET | `/qr` | PNG sin caché |
| POST / GET | `/groups/sync` | Iniciar / consultar sincronización |
| PUT | `/announcements/groups` | Guardar selección por IDs |
| PUT | `/announcements/enabled` | Habilitar/deshabilitar anuncios |
| GET | `/products/{id}/preview` | Comprobar disponibilidad y preparar mensaje |
| POST | `/products/{id}/promotions` | Enviar con `Idempotency-Key` |

La configuración del proveedor, sus credenciales, programación y tokens n8n siguen en el servidor/web. No se ejecutan automatizaciones desde el teléfono. Un servidor anterior continúa atendiendo los demás módulos; el módulo WhatsApp indica que necesita actualizarse ante un 404.

## Validación

Las pruebas usan H2, servicios simulados y MockWebServer: no envían mensajes reales. Cubren JWT y permisos, aislamiento de tiendas en servicios, sincronización, selección por IDs, vista previa, envío individual/grupos, resultados parciales, reintentos con la misma clave y recuperación del checkpoint tras reiniciar.

El APK final se compila sin `lanHost`, con HTTPS obligatorio. Las pruebas de transporte del emulador usan temporalmente `-PlanHost=10.0.2.15`. Consultar `VALIDATION.md` para los resultados de esta ejecución.
