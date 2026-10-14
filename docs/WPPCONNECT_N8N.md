# WPPConnect y n8n

## Configuración del servicio

Tienda POS no administra Docker ni modifica sesiones remotas. Agrega al servicio systemd:

```ini
[Service]
Environment="TIENDA_WPPCONNECT_MASTER_KEY=BASE64_DE_32_BYTES"
Environment="TIENDA_WPPCONNECT_ALLOWED_HOSTS=127.0.0.1,localhost"
Environment="TIENDA_WPPCONNECT_CONNECT_TIMEOUT=3s"
Environment="TIENDA_WPPCONNECT_READ_TIMEOUT=12s"
```

Genera la clave maestra una sola vez con `openssl rand -base64 32`, guárdala fuera del repositorio y conserva una copia segura. Aplica el cambio con `systemctl daemon-reload` y `systemctl restart tienda-pos`.

En **Plataforma > WPPConnect**, registra `http://127.0.0.1:21465` y la `SECRET_KEY`, prueba la conexión y asocia la sesión existente `tienda` a la tienda correcta. Asociarla no ejecuta logout, no borra tokens ni cambia webhooks.

## Vincular una tienda y consultar grupos

En **Administración > WhatsApp**, selecciona **Conectar WhatsApp**. Si la sesión requiere vinculación, escanea el QR desde **WhatsApp > Dispositivos vinculados**. La pantalla consulta el estado automáticamente.

Con la sesión conectada, pulsa **Actualizar grupos**. Los identificadores se conservan completos con `@g.us`. Puedes copiar un ID, varios IDs, JSON puro o JavaScript listo para un nodo Code de n8n.

## Credencial Bearer

Genera una credencial con los permisos necesarios. El secreto aparece una sola vez. En n8n configura un nodo **HTTP Request** con el encabezado `Authorization: Bearer tip_...`.

```http
GET /api/integrations/v1/default/whatsapp/status
GET /api/integrations/v1/default/whatsapp/groups
POST /api/integrations/v1/default/whatsapp/send
```

Texto JSON:

```json
{"phone":"120363000000000000@g.us","isGroup":true,"message":"Productos disponibles"}
```

Para una imagen o PDF usa `multipart/form-data` con `phone`, `isGroup`, `caption` y `file`. Se aceptan PNG, JPEG, WebP y PDF reales de hasta 10 MB. Tienda POS no descarga archivos desde URLs.

```javascript
const destinatarios = [
  { phone: '120363000000000000@g.us', isGroup: true }
];

return [{ json: { destinatarios, titulo: 'Productos disponibles' } }];
```

Los códigos `401`, `403`, `422`, `429` y `502` indican credencial inválida, alcance o tienda incorrectos, solicitud inválida o sesión desconectada, exceso de solicitudes y fallo del proveedor.

## Seleccionar grupos publicitarios

En **Administración > Integraciones > WhatsApp**:

1. Pulsa **Actualizar grupos** con WhatsApp conectado.
2. Usa la primera casilla únicamente para copiar IDs.
3. Activa **Enviar anuncios** en cada destinatario autorizado.
4. Pulsa **Guardar grupos para anuncios**. El botón solo se habilita cuando hay cambios pendientes.
5. Activa **Habilitar anuncios automáticos por WhatsApp** y guarda la configuración.

Las selecciones se conservan si un grupo desaparece temporalmente, pero ese grupo no se entrega a la API mientras esté marcado como no disponible. Cambiar de número, proveedor o conexión deshabilita los anuncios y elimina las autorizaciones anteriores; hay que actualizar y confirmar nuevamente los grupos. Una reconexión normal del mismo número no altera las selecciones.

La tarjeta **Anuncios automáticos** muestra grupos disponibles, seleccionados, seleccionados disponibles, estado de WhatsApp y el estado final. Solamente `LISTO` significa que una automatización externa puede operar. Otros valores posibles son `DESHABILITADO`, `SIN_GRUPOS`, `SIN_SESION`, `DESCONECTADO`, `PROVEEDOR_NO_DISPONIBLE` y `TIENDA_SUSPENDIDA`.

## Credencial central multitienda

El administrador principal entra en **Plataforma > WPPConnect > Credenciales centrales de automatización**. Asigna un nombre, selecciona `ANNOUNCEMENTS_READ` y/o `ANNOUNCEMENTS_SEND` y genera la credencial. El token `tap_...` se muestra una sola vez, se almacena únicamente como hash y puede revocarse desde la misma pantalla.

Esta credencial no sustituye los tokens `tip_...` de una tienda. No permite administrar tiendas, usuarios, inventario, finanzas, proveedores ni sesiones. Guárdala en el almacén de credenciales de n8n y envíala exclusivamente como `Authorization: Bearer tap_...`.

## API central de automatización

Base: `/api/automation/v1/whatsapp`. Todos los ejemplos requieren la credencial central.

### Tiendas aptas

```http
GET /api/automation/v1/whatsapp/stores/eligible
Authorization: Bearer tap_EJEMPLO
```

```json
{"stores":[{"tenantCode":"default","tenantName":"Tienda principal","whatsappStatus":"CONNECTED","announcementsEnabled":true,"selectedGroups":3,"ready":true}]}
```

La lista usa el estado reciente mantenido por el monitor; no consulta WPPConnect una vez por tienda. Si el estado está vencido, la tienda no aparece.

### Grupos autorizados

```http
GET /api/automation/v1/whatsapp/stores/default/groups
Authorization: Bearer tap_EJEMPLO
```

Solo se devuelven grupos seleccionados, disponibles y pertenecientes a la conexión confirmada:

```json
{"tenantCode":"default","announcementsEnabled":true,"groups":[{"id":"120363000000000001@g.us","name":"Ofertas","isGroup":true}]}
```

### Productos promocionales

```http
GET /api/automation/v1/whatsapp/stores/default/products/promotions?page=0&size=100
Authorization: Bearer tap_EJEMPLO
```

Incluye únicamente productos activos, con stock y `promocionWhatsapp=true`.

### Catálogo completo

```http
GET /api/automation/v1/whatsapp/stores/default/products/catalog?page=0&size=100
Authorization: Bearer tap_EJEMPLO
```

Incluye todos los productos activos con stock, aunque no sean promocionales. Ambos endpoints entregan DTO sin costos, márgenes ni proveedores, ordenados por categoría, marca, nombre e ID. `size` admite de 1 a 250 y las imágenes locales se convierten en URLs HTTP absolutas.

### Enviar texto a un grupo autorizado

```http
POST /api/automation/v1/whatsapp/stores/default/send
Authorization: Bearer tap_EJEMPLO
Idempotency-Key: campana-2026-10-13-default-grupo-1-producto-42
Content-Type: application/json

{"groupId":"120363000000000001@g.us","message":"Producto disponible"}
```

### Enviar imagen o PDF

```http
POST /api/automation/v1/whatsapp/stores/default/send
Authorization: Bearer tap_EJEMPLO
Idempotency-Key: catalogo-2026-10-13-default-grupo-1
Content-Type: multipart/form-data

groupId=120363000000000001@g.us
caption=Catálogo actualizado
file=@catalogo.pdf
```

`Idempotency-Key` es obligatoria en ambos envíos. Repetir la misma clave y solicitud devuelve el resultado guardado; reutilizarla con otro contenido se rechaza. El backend resuelve la tienda, sesión y proveedor. Nunca acepta `tenantId`, `sessionName` ni `providerId` enviados por n8n, y rechaza números individuales y grupos no autorizados antes de contactar WPPConnect.

## Errores y seguridad

- `401`: token ausente, inválido, vencido o revocado.
- `403`: falta `ANNOUNCEMENTS_READ` o `ANNOUNCEMENTS_SEND`.
- `404`: tienda inexistente.
- `400`: paginación, archivo o solicitud inválida.
- `422`: tienda no apta, anuncios deshabilitados, identidad no confirmada, sesión desconectada o grupo no autorizado.
- `429`: límite de solicitudes excedido.
- `502`: WPPConnect rechazó o no pudo completar la operación.

No escribas tokens en logs, workflows exportados ni repositorios. Revoca inmediatamente una credencial expuesta. Tienda POS registra uso, ruta, código HTTP y origen de las llamadas centrales sin almacenar el token completo.
