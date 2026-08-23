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
```

`config/application.yml`, `.env`, `logs/` y `backups/` están ignorados por Git.

## Módulos incluidos

- Setup inicial sin datasource externo.
- Login/logout con Spring Security, BCrypt, CSRF y roles `ROLE_ADMIN` / `ROLE_CAJERO`.
- Dashboard.
- Productos, categorías y proveedores, con alta asistida por código de barras usando Open Food Facts.
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

## Logs

Los logs se escriben en:

```text
logs/tienda-pos.log
```

con rotación por tamaño e historial.

## Backup

La pantalla de configuración incluye una acción inicial de respaldo. Si `mysqldump` o `mariadb-dump` está disponible en el servidor, úsalo para generar respaldos completos en `backups/`.

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
