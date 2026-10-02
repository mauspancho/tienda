# Tienda POS

Sistema web monolítico de Punto de Venta e Inventario para una tienda de abarrotes.

## Requisitos

- Java 21.
- Maven 3.9+.
- MariaDB o MySQL.
- Node.js solo si deseas recompilar Tailwind durante desarrollo. El JAR ya incluye `static/css/app.css`.

## Compilación

```bash
mvn clean test
mvn clean package
```

El ejecutable queda en:

```text
target/tienda-pos.jar
```

## Primera ejecución

```bash
java -jar target/tienda-pos.jar
```

Si no existe `./config/application.yml`, la aplicación arranca en modo instalación y muestra `/setup`.

El wizard solicita:

- conexión MariaDB/MySQL;
- primer administrador;
- datos básicos de la tienda.

Después de validar la conexión ejecuta Flyway, crea catálogos iniciales, inserta el administrador con BCrypt y escribe `config/application.yml`. Reinicia la aplicación para entrar a `/admin/login`.

## Estructura externa

```text
tienda-pos/
├── tienda-pos.jar
├── config/application.yml
├── logs/
├── backups/
└── data/
    ├── products/
    └── catalog/
```

`config/application.yml`, `.env`, `logs/`, `backups/` y `data/` están ignorados por Git.

## Módulos incluidos

- Setup inicial sin datasource externo.
- Login/logout con Spring Security, BCrypt, CSRF y roles `ROLE_ADMIN` / `ROLE_CAJERO`.
- Dashboard administrativo bajo /admin.
- Catalogo publico en / y detalle en /producto/{id} sin login.
- Productos, categorías y proveedores, con imagenes locales publicas y alta asistida por codigo de barras usando Open Food Facts.
- Compras con actualización transaccional de inventario.
- Inventario con movimientos trazables.
- POS con lector USB tipo teclado y camara movil.
- Ventas, pagos y ticket térmico aproximado a 80 mm.
- Gastos, reportes esenciales, usuarios, caja y configuración.
- Migraciones Flyway iniciales para MariaDB/MySQL.

## Base de datos

Flyway administra el esquema. En modo normal se usa:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

No se crea usuario administrador por defecto. Se crea únicamente desde el setup.

## Tailwind

Para desarrollo puedes recompilar CSS:

```bash
npm install
npm run css:build
```

No es necesario Node.js para ejecutar el JAR.

## Catalogo publico

La raiz `/` muestra un catalogo publico sin login. El area operativa y administrativa vive bajo `/admin/**`; el login queda en `/admin/login`.

Desde `Configuracion` puedes activar o desactivar el catalogo, cambiar titulo/subtitulo, definir el titulo de promociones y subir el logo publico. El logo se guarda fuera del JAR en:

```text
data/catalog/
```

Los productos marcados como promocionados se muestran en el slider publico, con un maximo de 4. Las imagenes publicas permitidas por seguridad son HTTPS o rutas locales bajo `/uploads/products/` y `/uploads/catalog/`.

## Imagenes de productos

Los productos conservan la referencia final en la columna `product.image_url`. Si subes un archivo manual, la aplicación guarda una copia optimizada fuera del JAR en:

```text
data/products/
```

Las imágenes locales se publican como:

```text
/uploads/products/{uuid}.jpg
```

La configuración por defecto es:

```yaml
tienda:
  product-images:
    directory: ./data/products
    public-path: /uploads/products
    max-upload-size: 5MB
    max-width: 800
    max-height: 800
    webp-quality: 0.82
```

El formulario acepta JPEG, PNG o WebP de hasta 5 MB, valida que el archivo sea una imagen real y genera nombres UUID para evitar usar nombres originales. Para no agregar dependencias nuevas, la copia optimizada se guarda como JPEG. Al reemplazar o quitar una imagen solo se eliminan archivos locales bajo `/uploads/products/`; nunca se borra una URL externa.

Cuando Open Food Facts proporciona una imagen, la aplicación intenta descargarla y guardarla como imagen local. Así, el catálogo y la API no dependen de que el servidor externo siga disponible. Si la descarga falla, el alta puede continuar sin imagen; una imagen manual reemplaza la referencia anterior.
## Logs

Los logs se escriben en:

```text
logs/tienda-pos.log
```

con rotación por tamaño e historial.

## Backup

La pantalla de configuración incluye una acción inicial de respaldo. Si `mysqldump` o `mariadb-dump` está disponible en el servidor, úsalo para generar respaldos completos en `backups/`. Ademas respalda `data/products/` y `data/catalog/`, porque ahi viven las imagenes locales de productos y el logo publico; no van dentro del JAR ni de la base de datos.

## Roles

- `ADMIN`: acceso completo.
- `CAJERO`: POS y ventas propias. La administracion de caja, reportes, inventario y configuracion queda para administradores.

## Troubleshooting

- Si vuelve a aparecer `/setup`, revisa que exista `config/application.yml` y contenga `spring.datasource.url`.
- Si Flyway falla, revisa usuario, permisos y que la base exista.
- No publiques `config/application.yml`: contiene credenciales.
## Integración Open Food Facts

La pantalla `Productos -> Nuevo producto` incluye un bloque para escanear o escribir un código de barras y presionar Enter. El sistema busca primero en la base local; si el producto ya existe, muestra accesos para verlo o editarlo. Si no existe localmente, consulta Open Food Facts y precarga datos descriptivos cuando están disponibles:

- nombre;
- marca;
- presentación;
- categoría sugerida;
- URL de imagen.

La integración descarga localmente la imagen disponible, pero nunca obtiene stock, costo ni precio desde Open Food Facts. Esos datos siguen siendo propios de la tienda y deben capturarse manualmente antes de guardar.

La configuración por defecto es:

```yaml
external:
  products:
    open-food-facts:
      enabled: true
      base-url: https://world.openfoodfacts.org
      user-agent: TiendaPOS/1.0
      connect-timeout: 3s
      read-timeout: 5s
```

Puedes deshabilitarla en `config/application.yml` con:

```yaml
external:
  products:
    open-food-facts:
      enabled: false
```

Si Open Food Facts no responde, la aplicación permite continuar el alta manual con el código de barras escaneado. Durante una venta normal el POS solo usa la base local; si el producto no existe muestra un acceso para registrarlo desde Productos.

## Migraciones nuevas

`V3__add_external_product_fields.sql` agrega columnas opcionales a `product`:

- `brand`
- `presentation`
- `image_url`

Flyway aplicara esta migracion al reiniciar el JAR actualizado. No modifica ni recrea tablas existentes.

V4__add_public_catalog_fields.sql agrega los campos del catalogo publico: product.promoted, product.promotion_order, business_settings.catalog_enabled, business_settings.catalog_title, business_settings.catalog_subtitle y business_settings.promotion_title.

## Escáner con cámara móvil

La pantalla `Productos -> Nuevo producto` y el `Punto de Venta` incluyen botones para leer códigos de barras con la cámara del dispositivo. La lectura usa `@zxing/browser` desde assets locales incluidos en el JAR; no se carga ninguna librería desde CDN.

La cámara solo convierte el código físico a texto. Después reutiliza los flujos existentes:

- en Productos llama la búsqueda asistida por código de barras y Open Food Facts si aplica;
- en POS llama la búsqueda local y agrega el producto al carrito;
- si el producto no existe en POS, muestra la opción para registrarlo.

El video se procesa localmente en el navegador. No se envían imágenes ni video al servidor; únicamente se usa el string del código detectado.

En teléfonos y navegadores modernos la cámara requiere un contexto seguro. Usa HTTPS cuando accedas desde otro dispositivo de la red. El lector USB tipo teclado y la captura manual siguen funcionando por HTTP.

Pruebas manuales sugeridas:

- Productos: abrir `Productos -> Nuevo producto`, tocar `Escanear con cámara`, escanear un EAN/UPC y verificar que se precargue el formulario o permita alta manual.
- POS: abrir caja, tocar `Usar cámara`, escanear varios productos y confirmar que el carrito suma cantidades repetidas sin cerrar la cámara.
- Producto no registrado: escanear un código inexistente y verificar las opciones `Buscar informacion y registrar` y `Continuar escaneando`.
- Permisos: denegar cámara y confirmar que aparece un mensaje claro sin romper captura manual ni lector USB.


## Finanzas

Los administradores pueden entrar a `Administracion -> Finanzas` para revisar ventas, costo vendido, ganancia bruta, gastos, utilidad neta, compras, reinversion, aportaciones del propietario, retiros, inventario valorizado y recuperacion de inversion inicial.

La utilidad usa los costos historicos guardados en cada `SaleItem` (`unitCost` y `profit`), por lo que cambiar el costo actual de un producto no recalcula ventas antiguas.

En `Compras` se captura el origen del dinero:

- `Caja / ventas`: cuenta como reinversion del negocio.
- `Capital propietario`: cuenta como aportacion del propietario.
- `Credito proveedor`, `Otro` y `Sin clasificar`: quedan separados para analisis financiero.

La pantalla `Finanzas -> Historico diario` permite consultar por fecha y exportar CSV. La pantalla `Finanzas -> Capital` permite registrar inversion inicial, aportaciones, retiros y ajustes manuales.

`V7__add_finance_module.sql` agrega `capital_movement`, `purchase.funding_source` e indices para consultas financieras.

## API REST v1

La interfaz web y la API comparten los mismos Services y Repositories. La web conserva Form Login, sesión `JSESSIONID` y CSRF bajo `/admin/**`; la API usa JSON, es stateless y acepta Bearer Token exclusivamente bajo `/api/v1/**`.

La URL base es:

```text
http://servidor:8080/api/v1
```

HTTP puede utilizarse durante desarrollo o dentro de una red local controlada. Para acceso externo o producción debe publicarse detrás de HTTPS.

### Autenticación

- `POST /api/v1/auth/login`: valida los mismos usuarios y contraseñas de la web y devuelve access token, refresh token y usuario.
- `POST /api/v1/auth/refresh`: rota el refresh token y entrega un par nuevo.
- `POST /api/v1/auth/logout`: revoca el refresh token indicado.
- `GET /api/v1/auth/me`: devuelve el usuario autenticado y sus roles.

El access token dura 15 minutos y el refresh token 30 días por defecto. Los refresh tokens se guardan como hash SHA-256, tienen expiración y revocación, y la reutilización de un token ya rotado revoca las sesiones activas del usuario.

Configura un secreto estable y de al menos 32 bytes antes de usar la API en producción. Si se omite, se genera uno temporal al arrancar y todos los access tokens dejan de funcionar en el siguiente reinicio.

```text
TIENDA_API_JWT_SECRET=una-clave-aleatoria-de-32-bytes-o-mas
TIENDA_API_ISSUER=tienda-pos
TIENDA_API_ACCESS_TOKEN_TTL=15m
TIENDA_API_REFRESH_TOKEN_TTL=30d
TIENDA_API_CORS_ALLOWED_ORIGINS=https://app.ejemplo.mx,https://admin.ejemplo.mx
TIENDA_API_DOCS_ENABLED=true
```

Los orígenes CORS están vacíos por defecto y se configuran como una lista separada por comas. No se habilitan credenciales CORS ni comodines. Una aplicación Android nativa no necesita CORS.

### Compatibilidad y documentación

- `GET /api/v1/health`: público y estable; identifica aplicación, versión de API y versión del servidor.
- `GET /api/v1/info`: público; publica únicamente información no sensible de compatibilidad.
- OpenAPI JSON: `/api-docs`.
- Swagger UI: `/swagger-ui`.

OpenAPI describe únicamente `/api/v1/**`. En producción puede deshabilitarse con `TIENDA_API_DOCS_ENABLED=false`.

### Recursos

- Productos: `/products`, `/products/{id}`, `/products/search`, `/products/code/{code}`, `/products/barcode/{barcode}`, alta y edición JSON/multipart, estado y promociones.
- Catálogos: `/categories` y `/suppliers` con listado paginado, detalle, alta, edición y estado.
- POS y ventas: `/pos`, `/pos/checkout`, `/sales`, `/sales/{folio}` y `/tickets/{folio}`.
- Compras: `/purchases` con histórico, detalle y registro.
- Inventario: `/inventory/stock`, `/inventory/movements`, ajustes y reversión.
- Caja: `/cash/current`, apertura, cierre e histórico de sesiones con movimientos.
- Administración: `/expenses`, `/finances`, `/reports`, `/users` y `/settings`.
- Indicadores: `/dashboard` y `/dashboard/profit`.

Los listados que pueden crecer usan `page`, `size` y, cuando aplica, `sort=campo,asc|desc`. El contrato paginado contiene `content`, `page`, `size`, `totalElements` y `totalPages`. Fechas se entregan en ISO-8601 e importes como `BigDecimal`, sin símbolos monetarios.

`ROLE_ADMIN` administra todos los módulos. `ROLE_CAJERO` puede consultar productos activos, usar POS, abrir/cerrar su caja, registrar ventas, consultar sus propias ventas y ver los indicadores que ya permite la web. El servidor valida los permisos; no depende de que el cliente oculte acciones.

`POST /api/v1/sales` y `POST /api/v1/purchases` aceptan opcionalmente `Idempotency-Key`. Repetir la misma clave con el mismo cuerpo devuelve el resultado original sin duplicar la operación; reutilizarla con otro cuerpo responde `409 Conflict`.

### Ejemplos curl

```bash
curl http://localhost:8080/api/v1/health
```

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"contraseña"}'
```

```bash
curl "http://localhost:8080/api/v1/products?page=0&size=20&sort=name,asc" \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

Las migraciones `V9__add_api_refresh_tokens.sql` y `V10__add_api_idempotency.sql` crean el almacenamiento de refresh tokens y respuestas idempotentes sin alterar las tablas existentes.
