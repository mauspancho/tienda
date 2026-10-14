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
