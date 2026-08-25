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

Después de validar la conexión ejecuta Flyway, crea catálogos iniciales, inserta el administrador con BCrypt y escribe `config/application.yml`. Reinicia la aplicación para entrar a `/login`.

## Estructura externa

```text
tienda-pos/
├── tienda-pos.jar
├── config/application.yml
├── logs/
├── backups/
└── data/
    └── products/
```

`config/application.yml`, `.env`, `logs/`, `backups/` y `data/` están ignorados por Git.

## Módulos incluidos

- Setup inicial sin datasource externo.
- Login/logout con Spring Security, BCrypt, CSRF y roles `ROLE_ADMIN` / `ROLE_CAJERO`.
- Dashboard.
- Productos, categorías y proveedores, con imágenes locales y alta asistida por código de barras usando Open Food Facts.
- Compras con actualización transaccional de inventario.
- Inventario con movimientos trazables.
- POS con lector USB tipo teclado (`input + Enter`).
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

## Imágenes de productos

Los productos conservan la referencia final en la columna `product.image_url`. Si escribes una URL externa, solo se guarda la URL; si subes un archivo manual, la aplicación guarda una copia optimizada fuera del JAR en:

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

Open Food Facts puede precargar una URL de imagen, pero la aplicación no la descarga automáticamente. Si subes una imagen manualmente, esa imagen reemplaza la URL externa.
## Logs

Los logs se escriben en:

```text
logs/tienda-pos.log
```

con rotación por tamaño e historial.

## Backup

La pantalla de configuración incluye una acción inicial de respaldo. Si `mysqldump` o `mariadb-dump` está disponible en el servidor, úsalo para generar respaldos completos en `backups/`. Además respalda `data/products/`, porque ahí viven las imágenes locales de productos y no van dentro del JAR ni de la base de datos.

## Roles

- `ADMIN`: acceso completo.
- `CAJERO`: POS, ventas y caja.

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

La integración no descarga imágenes al servidor y nunca obtiene stock, costo ni precio desde Open Food Facts. Esos datos siguen siendo propios de la tienda y deben capturarse manualmente antes de guardar.

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

Flyway aplicará esta migración al reiniciar el JAR actualizado. No modifica ni recrea tablas existentes.

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
