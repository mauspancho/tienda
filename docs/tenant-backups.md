# Respaldos por tienda

Disponible en `commercial-v1`, adaptado del respaldo monotienda de `main` (`7207bde`).

## Operacion

1. Iniciar sesion como administrador de plataforma (`ROLE_PLATFORM_ADMIN`).
2. En Tiendas, elegir **Importar / Exportar** junto a **Ver**.
3. Descargar el ZIP antes de reemplazar datos.
4. Suspender la tienda destino desde su ficha.
5. Subir el ZIP y escribir `IMPORTAR <codigo-de-la-tienda>`.
6. Revisar los datos y usuarios antes de reactivar la tienda.

Se acepta el formato `tienda-pos-store-backup`, version 1, schema 12 de main,
y el nuevo formato `tienda-pos-tenant-backup`, version 1, schema 15.
El ZIP nuevo no se debe restaurar en main: contiene inventario y estructura multi-tienda.

## Alcance y seguridad

- La ruta del panel determina la tienda destino. El identificador del manifiesto
  solo comprueba la coherencia interna del archivo; nunca elige el destino.
- Se reemplazan productos, categorias, proveedores, clientes, compras, ventas,
  pagos, creditos, existencias, cajas, movimientos, configuracion y auditoria de esa tienda.
- Los IDs y las referencias se reasignan. Las referencias ausentes se rechazan;
  nunca se buscan registros de otras tiendas como alternativa.
- No se importan roles globales, contrasenas, tokens ni sesiones de acceso.
  Las cuentas existentes conservan sus credenciales y permisos. Los usuarios
  historicos nuevos se crean desactivados y como cajeros. Si su nombre esta
  ocupado en otra tienda se asigna un nombre `import-<UUID>`.
- Se invalidan los tokens y sesiones operativas del destino. No se reactiva la tienda.
- Solo se exportan imagenes referenciadas por sus productos/configuracion.
  Las imagenes importadas reciben nombres nuevos; nunca se reemplazan directorios
  compartidos. Los archivos antiguos se conservan para no afectar otras referencias.
- El reemplazo de datos es transaccional. Si falla, se restauran los datos previos
  y se eliminan las imagenes nuevas de esa importacion.
- Se conserva la restriccion actual de folios de venta unicos en toda la base:
  si un ZIP contiene un folio ocupado por otra tienda, se rechaza sin modificarla.
- Los ZIP de main usan la estructura comercial ya creada en el destino y generan
  `inventory_stock` desde las existencias y minimos de cada producto.

## Limites

ZIP: 256 MB. Contenido descomprimido: 512 MB. JSON total: 64 MB.
Hasta 10,000 entradas; imagen individual de hasta 5 MB. No se admiten rutas
relativas, directorios, nombres duplicados ni archivos fuera del formato permitido.
El servidor mantiene el limite propio de 5 MB de los servicios de carga de imagenes.
Para respaldos mayores se requiere otro procedimiento administrativo; no dividir
manualmente un respaldo en varias importaciones.

La suite `PlatformIntegrationTest` prueba rutas y CSRF, aislamiento entre tiendas,
rollback ante referencias externas, imagenes y compatibilidad con el formato de main.
