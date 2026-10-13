package com.tienda.pos.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.catalog.CatalogImagesProperties;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.ProductImagesProperties;
import com.tienda.pos.security.SessionRevocationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Explicit tenant scope; archive identifiers are never used as database identifiers. */
@Service
@NormalMode
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantBackupService {
    static final String FORMAT = "tienda-pos-tenant-backup";
    static final List<String> TABLES = List.of("business", "branch", "warehouse", "cash_register",
            "category", "supplier", "customer", "product", "inventory_stock", "purchase", "purchase_item",
            "sale", "sale_item", "payment", "cash_register_session", "expense_category", "expense",
            "capital_movement", "credit_charge", "credit_payment", "credit_payment_application",
            "inventory_movement", "cash_movement", "business_settings", "audit_log");
    private static final List<String> LEGACY_TABLES = List.of("role", "app_user", "user_roles", "category",
            "supplier", "customer", "product", "purchase", "purchase_item", "sale", "sale_item", "payment",
            "inventory_movement", "cash_register_session", "cash_movement", "expense_category", "expense",
            "business_settings", "audit_log", "capital_movement", "credit_charge", "credit_payment",
            "credit_payment_application", "api_refresh_token", "api_idempotency_record");
    private static final Set<String> STRUCTURE = Set.of("business", "branch", "warehouse", "cash_register");
    private static final Set<String> SECRETS = Set.of("password_hash", "auth_epoch", "last_login");
    private static final long MAX_ZIP = 256L * 1024 * 1024, MAX_DATA = 64L * 1024 * 1024;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SessionRevocationService sessions;
    private final Path products, catalog;
    private final String productPath, catalogPath;

    public TenantBackupService(JdbcTemplate db, ObjectMapper json, SessionRevocationService sessions,
                               ProductImagesProperties products, CatalogImagesProperties catalog) {
        this.db = db; this.json = json; this.sessions = sessions;
        this.products = Path.of(products.getDirectory()).toAbsolutePath().normalize();
        this.catalog = Path.of(catalog.getDirectory()).toAbsolutePath().normalize();
        this.productPath = products.normalizedPublicPath(); this.catalogPath = catalog.normalizedPublicPath();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void exportArchive(long tenant, OutputStream output) throws IOException {
        requireTenant(tenant, false);
        List<Map<String, Object>> definitions = new ArrayList<>();
        Set<String> images = new LinkedHashSet<>();
        List<String> tables = new ArrayList<>(TABLES); tables.addFirst("app_user");
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String table : tables) {
                List<String> columns = new ArrayList<>(columns(table).keySet());
                if (table.equals("app_user")) columns.removeAll(SECRETS);
                long count = db.queryForObject("select count(*) from " + q(table) + " where " + scope(table), Long.class, tenant);
                definitions.add(Map.of("name", table, "columns", columns.stream().map(c -> Map.of("name", c)).toList(), "rowCount", count));
                zip.putNextEntry(new ZipEntry("data/" + table + ".json"));
                var writer = json.getFactory().createGenerator(zip);
                writer.disable(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                writer.writeStartArray();
                db.query("select * from " + q(table) + " where " + scope(table) + " order by id", rs -> {
                    try {
                        writer.writeStartArray();
                        for (String column : columns) {
                            Object value = rs.getObject(column);
                            String text = value == null ? null : value instanceof Boolean b ? b.toString() : rs.getString(column);
                            writer.writeString(text);
                            if (isImage(table, column) && text != null && !text.isBlank() && !text.startsWith("https://")) images.add(text);
                        }
                        writer.writeEndArray();
                    } catch (IOException ex) { throw new UncheckedIOException(ex); }
                }, tenant);
                writer.writeEndArray(); writer.close(); zip.closeEntry();
            }
            for (String reference : images) {
                ImageLocation location = imageLocation(reference);
                Path file = location.directory().resolve(location.name());
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !file.toRealPath().startsWith(location.directory().toRealPath()))
                    throw problem("Falta una imagen del respaldo: " + location.name());
                zip.putNextEntry(new ZipEntry("images/" + location.kind() + "/" + location.name()));
                Files.copy(file, zip); zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(json.writeValueAsBytes(Map.of("format", FORMAT, "formatVersion", 1, "schemaVersion", 15,
                    "sourceTenant", tenant, "exportedAt", LocalDateTime.now().toString(), "tables", definitions)));
            zip.closeEntry();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void importArchive(long tenant, MultipartFile file) throws IOException {
        requireTenant(tenant, true);
        if (file == null || file.isEmpty() || file.getSize() > MAX_ZIP) throw problem("Selecciona un ZIP de hasta 256 MB.");
        Path temp = Files.createTempDirectory("tenant-backup-");
        List<Path> createdImages = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) createdImages.forEach(TenantBackupService::deleteFile);
            }
        });
        try {
            extract(file, temp);
            JsonNode manifest = json.readTree(temp.resolve("manifest.json").toFile());
            if (manifest == null || !manifest.isObject() || !manifest.path("tables").isArray()) throw problem("Manifiesto invalido.");
            boolean legacy = "tienda-pos-store-backup".equals(manifest.path("format").asText());
            if ((!legacy && !FORMAT.equals(manifest.path("format").asText()))
                    || manifest.path("formatVersion").asInt() != 1
                    || manifest.path("schemaVersion").asInt() != (legacy ? 12 : 15)) throw problem("Formato o version de respaldo incompatible.");
            Map<String, List<Map<String, String>>> data = readData(temp, manifest, legacy);
            Map<String, Map<String, Long>> ids = new HashMap<>();
            // Preserve access configuration. Historical users never carry credentials or platform roles.
            mapUsers(tenant, data.get("app_user"), ids);
            Map<String, Long> defaults = new HashMap<>();
            if (legacy) for (String table : STRUCTURE) defaults.put(table, db.queryForObject(
                    "select min(id) from " + q(table) + " where tenant_id=?", Long.class, tenant));
            List<String> reversed = new ArrayList<>(TABLES); Collections.reverse(reversed);
            for (String table : reversed) {
                if (!(legacy && STRUCTURE.contains(table))) db.update("delete from " + q(table) + " where " + scope(table), tenant);
            }
            Map<String, String> imageMap = new HashMap<>();
            for (String table : TABLES) {
                if (legacy && (STRUCTURE.contains(table) || table.equals("inventory_stock"))) continue;
                Map<String, Integer> metadata = columns(table);
                for (Map<String, String> original : data.get(table)) {
                    Map<String, String> row = new LinkedHashMap<>(original);
                    String oldId = row.remove("id"); row.remove("tenant_id");
                    if (metadata.containsKey("tenant_id")) row.put("tenant_id", Long.toString(tenant));
                    for (String column : new ArrayList<>(row.keySet())) {
                        String parent = parent(table, column);
                        if (parent != null && row.get(column) != null) row.put(column, mapped(ids, parent, row.get(column)).toString());
                        if (isImage(table, column)) row.put(column, restoreImage(row.get(column), temp, imageMap, createdImages));
                    }
                    if (legacy) {
                        for (var entry : defaults.entrySet()) if (metadata.containsKey(entry.getKey() + "_id"))
                            row.put(entry.getKey() + "_id", Objects.requireNonNull(entry.getValue()).toString());
                    }
                    // Soft references are remapped in a second pass, after all their targets exist.
                    for (String column : List.of("reference_id", "reversal_movement_id", "entity_id")) row.remove(column);
                    if (table.equals("sale") && db.queryForObject("select count(*) from sale where folio=?", Long.class, row.get("folio")) > 0)
                        throw problem("El folio " + row.get("folio") + " ya existe en otra tienda. No se modifico ninguna tienda.");
                    long newId = insert(table, row, metadata);
                    ids.computeIfAbsent(table, k -> new HashMap<>()).put(oldId, newId);
                }
            }
            if (legacy) db.update("insert into inventory_stock(version,created_at,updated_at,tenant_id,product_id,warehouse_id,quantity,minimum_stock) "
                    + "select 0,current_timestamp,current_timestamp,tenant_id,id,?,current_stock,minimum_stock from product where tenant_id=?", defaults.get("warehouse"), tenant);
            remapSoftReferences(data, ids, tenant);
            db.update("delete from api_refresh_token where tenant_id=?", tenant);
            db.update("delete from api_idempotency_record where tenant_id=?", tenant);
            db.update("update tenant set auth_epoch=auth_epoch+1 where id=?", tenant);
            sessions.expireTenantSessions(tenant);
        } catch (org.springframework.dao.DataAccessException | IllegalArgumentException ex) {
            DomainException failure = problem("El respaldo contiene datos incompatibles. La importacion se deshizo; revisa el archivo.");
            failure.initCause(ex);
            throw failure;
        } finally { deleteTree(temp); }
    }

    private void requireTenant(long id, boolean importing) {
        List<Boolean> active = db.query("select active from tenant where id=?" + (importing ? " for update" : ""),
                (rs, n) -> rs.getBoolean(1), id);
        if (active.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (importing && active.getFirst()) throw problem("Suspende esta tienda antes de importar para evitar operaciones simultaneas.");
    }

    private Map<String, List<Map<String, String>>> readData(Path temp, JsonNode manifest, boolean legacy) throws IOException {
        Set<String> expected = new HashSet<>(legacy ? LEGACY_TABLES : TABLES); if (!legacy) expected.add("app_user");
        Map<String, List<Map<String, String>>> data = new HashMap<>();
        for (JsonNode definition : manifest.path("tables")) {
            String table = definition.path("name").asText();
            if (!expected.remove(table)) throw problem("Tabla inesperada o duplicada en el ZIP.");
            List<String> cols = new ArrayList<>();
            for (JsonNode col : definition.path("columns")) {
                String name = col.path("name").asText();
                if (!name.matches("[a-z0-9_]+") || cols.contains(name)) throw problem("Columnas invalidas.");
                cols.add(name);
            }
            JsonNode rows = json.readTree(temp.resolve("data/" + table + ".json").toFile());
            if (rows == null || !rows.isArray() || rows.size() != definition.path("rowCount").asLong(-1)) throw problem("Respaldo incompleto: " + table);
            // Legacy security tables are deliberately ignored, even for a platform administrator.
            if (!TABLES.contains(table) && !table.equals("app_user")) continue;
            Set<String> permitted = columns(table).keySet();
            if (!permitted.containsAll(cols) || !cols.contains("id")) throw problem("Columnas incompatibles: " + table);
            if (!legacy) {
                Set<String> required = new HashSet<>(permitted);
                if (table.equals("app_user")) required.removeAll(SECRETS);
                if (!required.equals(new HashSet<>(cols))) throw problem("Faltan columnas: " + table);
            }
            List<Map<String, String>> values = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() != cols.size()) throw problem("Fila invalida: " + table);
                Map<String, String> value = new LinkedHashMap<>();
                for (int i = 0; i < cols.size(); i++) {
                    if (!row.get(i).isNull() && !row.get(i).isValueNode()) throw problem("Valor invalido.");
                    value.put(cols.get(i), row.get(i).isNull() ? null : row.get(i).asText());
                }
                if (value.get("id") == null || !seen.add(value.get("id"))) throw problem("Identificador duplicado o ausente.");
                if (!legacy && permitted.contains("tenant_id") && !Objects.equals(value.get("tenant_id"), manifest.path("sourceTenant").asText()))
                    throw problem("El ZIP contiene registros de otra tienda.");
                values.add(value);
            }
            data.put(table, values);
        }
        if (!expected.isEmpty() || data.get("business_settings").size() != 1) throw problem("El respaldo esta incompleto.");
        if (!legacy) for (String table : STRUCTURE) if (data.get(table).isEmpty()) throw problem("Falta la estructura de la tienda.");
        return data;
    }

    private void mapUsers(long tenant, List<Map<String, String>> users, Map<String, Map<String, Long>> ids) {
        Map<String, Long> mapped = new HashMap<>(); ids.put("app_user", mapped);
        for (var source : users) {
            List<Long> existing = db.query("select id from app_user where tenant_id=? and username=?", (rs, n) -> rs.getLong(1), tenant, source.get("username"));
            if (!existing.isEmpty()) { mapped.put(source.get("id"), existing.getFirst()); continue; }
            Map<String, String> row = new LinkedHashMap<>(source);
            row.remove("id"); row.remove("last_login");
            row.put("tenant_id", Long.toString(tenant)); row.put("active", "false"); row.put("auth_epoch", "0");
            row.put("password_hash", "!imported-account-disabled-" + UUID.randomUUID());
            String username = source.get("username");
            if (username == null || username.isBlank()) throw problem("Usuario sin nombre en el respaldo.");
            if (db.queryForObject("select count(*) from app_user where username=?", Long.class, username) > 0)
                username = "import-" + UUID.randomUUID();
            row.put("username", username);
            long id = insert("app_user", row, columns("app_user"));
            db.update("insert into user_roles(user_id,role_id) select ?,id from role where name='ROLE_CAJERO'", id);
            mapped.put(source.get("id"), id);
        }
    }

    private void remapSoftReferences(Map<String, List<Map<String, String>>> data, Map<String, Map<String, Long>> ids, long tenant) {
        for (String table : List.of("inventory_movement", "cash_movement", "audit_log")) {
            for (var row : data.get(table)) {
                String column = table.equals("audit_log") ? "entity_id" : "reference_id";
                String kind = row.get(table.equals("audit_log") ? "entity_type" : "reference_type");
                String parent = switch (kind == null ? "" : kind.toUpperCase(Locale.ROOT)) {
                    case "SALE", "SALE_CANCELLATION" -> "sale"; case "PURCHASE" -> "purchase"; case "PRODUCT" -> "product";
                    case "REVERSAL", "INVENTORY_MOVEMENT" -> "inventory_movement";
                    case "CREDIT_PAYMENT", "CREDITPAYMENT", "CREDIT_PAYMENT_REVERSAL" -> "credit_payment";
                    case "CREDIT_CHARGE", "CREDITCHARGE" -> "credit_charge"; case "EXPENSE" -> "expense";
                    default -> null;
                };
                if (row.get(column) != null && parent != null) db.update("update " + q(table) + " set " + q(column) + "=? where id=? and tenant_id=?",
                        mapped(ids, parent, row.get(column)), mapped(ids, table, row.get("id")), tenant);
                if (row.get("reversal_movement_id") != null) db.update("update inventory_movement set reversal_movement_id=? where id=? and tenant_id=?",
                        mapped(ids, "inventory_movement", row.get("reversal_movement_id")), mapped(ids, table, row.get("id")), tenant);
            }
        }
    }

    private static String parent(String table, String column) {
        return switch (column) {
            case "business_id" -> "business"; case "branch_id" -> "branch"; case "warehouse_id" -> "warehouse";
            case "cash_register_id" -> "cash_register"; case "cash_register_session_id" -> "cash_register_session";
            case "user_id", "cashier_id" -> "app_user";
            case "category_id" -> table.equals("expense") ? "expense_category" : "category";
            case "supplier_id" -> "supplier"; case "customer_id" -> "customer"; case "product_id" -> "product";
            case "sale_id" -> "sale"; case "purchase_id" -> "purchase";
            case "credit_payment_id" -> "credit_payment"; case "credit_charge_id" -> "credit_charge";
            default -> null;
        };
    }

    private static Long mapped(Map<String, Map<String, Long>> ids, String table, String id) {
        Long value = ids.getOrDefault(table, Map.of()).get(id);
        if (value == null) throw problem("Referencia fuera del respaldo: " + table + ". No se permite enlazar otra tienda.");
        return value;
    }

    private long insert(String table, Map<String, String> row, Map<String, Integer> types) {
        var keys = new GeneratedKeyHolder();
        String sql = "insert into " + q(table) + " (" + String.join(",", row.keySet().stream().map(TenantBackupService::q).toList())
                + ") values (" + String.join(",", Collections.nCopies(row.size(), "?")) + ")";
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            for (var field : row.entrySet()) {
                String value = field.getValue(); int type = types.get(field.getKey());
                if (value == null) statement.setNull(i, type);
                else if (type == Types.BOOLEAN || type == Types.BIT) statement.setBoolean(i, value.equals("1") || value.equalsIgnoreCase("true"));
                else statement.setObject(i, value, type);
                i++;
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private Map<String, Integer> columns(String table) {
        return db.query("select * from " + q(table) + " where 1=0", rs -> {
            Map<String, Integer> cols = new LinkedHashMap<>(); ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) cols.put(meta.getColumnName(i).toLowerCase(Locale.ROOT), meta.getColumnType(i));
            return cols;
        });
    }

    private static String scope(String table) {
        return switch (table) {
            case "sale_item", "payment" -> "sale_id in (select id from sale where tenant_id=?)";
            case "purchase_item" -> "purchase_id in (select id from purchase where tenant_id=?)";
            default -> "tenant_id=?";
        };
    }

    private static String q(String identifier) {
        if (!identifier.matches("[a-z0-9_]+")) throw problem("Identificador invalido.");
        return "`" + identifier + "`";
    }

    private static boolean isImage(String table, String column) {
        return table.equals("product") && column.equals("image_url") || table.equals("business_settings") && column.equals("logo_path");
    }

    private record ImageLocation(String kind, Path directory, String name, String publicPath) {}

    private ImageLocation imageLocation(String reference) {
        boolean product = reference.startsWith(productPath + "/") || reference.startsWith("/uploads/products/");
        boolean cat = reference.startsWith(catalogPath + "/") || reference.startsWith("/uploads/catalog/");
        if (!product && !cat) throw problem("Ruta de imagen no permitida.");
        String filename = reference.substring(reference.lastIndexOf('/') + 1);
        if (!filename.matches("[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)")) throw problem("Nombre de imagen invalido.");
        String prefix = product ? productPath : catalogPath;
        if (!reference.equals(prefix + "/" + filename) && !reference.equals("/uploads/" + (product ? "products" : "catalog") + "/" + filename)) throw problem("Ruta de imagen invalida.");
        return new ImageLocation(product ? "products" : "catalog", product ? products : catalog, filename, prefix);
    }

    private String restoreImage(String reference, Path temp, Map<String, String> remapped, List<Path> created) throws IOException {
        if (reference == null || reference.isBlank()) return reference;
        if (reference.startsWith("https://")) return reference;
        if (remapped.containsKey(reference)) return remapped.get(reference);
        ImageLocation location = imageLocation(reference);
        Path source = temp.resolve("images/" + location.kind() + "/" + location.name());
        if (!Files.isRegularFile(source) || Files.size(source) > 5L * 1024 * 1024) throw problem("Imagen ausente o demasiado grande.");
        byte[] header;
        try (InputStream input = Files.newInputStream(source)) { header = input.readNBytes(12); }
        boolean valid = header.length >= 12 && ((header[0] == (byte) 0xff && header[1] == (byte) 0xd8)
                || (header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G')
                || (new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")));
        if (!valid) throw problem("Archivo de imagen invalido.");
        Files.createDirectories(location.directory());
        String name = UUID.randomUUID() + location.name().substring(location.name().lastIndexOf('.'));
        Path destination = location.directory().resolve(name);
        created.add(destination); Files.copy(source, destination);
        String result = location.publicPath() + "/" + name; remapped.put(reference, result); return result;
    }

    private static void extract(MultipartFile file, Path target) throws IOException {
        Set<String> names = new HashSet<>(); long total = 0, data = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (names.size() >= 10000 || !names.add(name) || name.contains("\\") || name.contains("..")
                        || !(name.equals("manifest.json") || name.matches("data/[a-z0-9_]+\\.json")
                        || name.matches("images/(products|catalog)/[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp)")))
                    throw problem("Entrada ZIP no permitida o duplicada.");
                Path path = target.resolve(name).normalize();
                if (!path.startsWith(target) || entry.isDirectory()) throw problem("Ruta ZIP invalida.");
                Files.createDirectories(path.getParent());
                try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read; if (!name.startsWith("images/")) data += read;
                        if (total > 512L * 1024 * 1024 || data > MAX_DATA) throw problem("El ZIP excede el limite descomprimido (512 MB; datos 64 MB).");
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        if (!names.contains("manifest.json")) throw problem("No se encontro el manifiesto del respaldo.");
    }

    private static DomainException problem(String message) { return new DomainException(message); }
    private static void deleteFile(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(TenantBackupService.class).warn("No se pudo limpiar {}", file, ex);
        }
    }
    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(TenantBackupService::deleteFile); }
    }
}
