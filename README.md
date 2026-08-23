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
- Productos, categorías y proveedores.
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
