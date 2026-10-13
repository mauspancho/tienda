package com.tienda.pos.backup;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.catalog.CatalogImagesProperties;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.ProductImagesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class StoreBackupService {

    private static final Logger log = LoggerFactory.getLogger(StoreBackupService.class);
    private static final String FORMAT = "tienda-pos-store-backup";
    private static final int FORMAT_VERSION = 1;
    private static final int SCHEMA_VERSION = 12;
    private static final long MAX_UNCOMPRESSED_SIZE = 5L * 1024 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final int BATCH_SIZE = 500;

    private static final List<String> TABLES = List.of(
            "role", "app_user", "user_roles", "category", "supplier", "customer", "product",
            "purchase", "purchase_item", "sale", "sale_item", "payment", "inventory_movement",
            "cash_register_session", "cash_movement", "expense_category", "expense",
            "business_settings", "audit_log", "capital_movement", "credit_charge", "credit_payment",
            "credit_payment_application", "api_refresh_token", "api_idempotency_record"
    );

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Path productImages;
    private final Path catalogImages;

    public StoreBackupService(DataSource dataSource, ObjectMapper objectMapper,
                              ProductImagesProperties productImagesProperties,
                              CatalogImagesProperties catalogImagesProperties) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.productImages = Path.of(productImagesProperties.getDirectory()).toAbsolutePath().normalize();
        this.catalogImages = Path.of(catalogImagesProperties.getDirectory()).toAbsolutePath().normalize();
    }

    public BackupInfo info() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            long records = 0;
            for (String table : TABLES) {
                records += countRows(connection, table);
            }
            return new BackupInfo(TABLES.size(), records, countFiles(productImages), countFiles(catalogImages));
        } catch (SQLException | IOException ex) {
            throw new DomainException("No fue posible consultar la información del respaldo.");
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    public String exportFilename() {
        return "tienda-pos-completo-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
    }

    @Transactional(readOnly = true)
    public void exportArchive(OutputStream outputStream) throws IOException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(outputStream), StandardCharsets.UTF_8)) {
            List<TableDefinition> definitions = tableDefinitions(connection);
            Manifest manifest = new Manifest(FORMAT, FORMAT_VERSION, SCHEMA_VERSION, LocalDateTime.now().toString(),
                    definitions, countFiles(productImages), countFiles(catalogImages));
            writeJsonEntry(zip, "manifest.json", manifest);
            for (TableDefinition definition : definitions) {
                writeTable(zip, connection, definition);
            }
            writeDirectory(zip, productImages, "images/products/");
            writeDirectory(zip, catalogImages, "images/catalog/");
            zip.finish();
        } catch (SQLException ex) {
            throw new IOException("No fue posible exportar la base de datos.", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void importArchive(MultipartFile archive) {
        if (archive == null || archive.isEmpty()) {
            throw new DomainException("Selecciona un archivo ZIP de respaldo.");
        }
        Path extracted = null;
        List<DirectorySwap> swaps = new ArrayList<>();
        try {
            extracted = Files.createTempDirectory("tienda-pos-import-");
            extractArchive(archive, extracted);
            Manifest manifest = readAndValidateManifest(extracted);
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                Map<String, TableDefinition> currentDefinitions = definitionsByName(tableDefinitions(connection));
                validateManifestTables(manifest, currentDefinitions, extracted);
                clearTables(connection);
                importTables(connection, manifest, currentDefinitions, extracted);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }

            Path productSource = extracted.resolve("images/products");
            Path catalogSource = extracted.resolve("images/catalog");
            Files.createDirectories(productSource);
            Files.createDirectories(catalogSource);
            validateImageTargets();
            swaps.add(swapDirectory(productSource, productImages));
            swaps.add(swapDirectory(catalogSource, catalogImages));
            registerSwapCompletion(swaps);
        } catch (DomainException ex) {
            restoreSwaps(swaps);
            throw ex;
        } catch (Exception ex) {
            restoreSwaps(swaps);
            log.error("Store backup import failed", ex);
            throw new DomainException("No fue posible importar el respaldo. No se aplicaron cambios.");
        } finally {
            deleteRecursively(extracted);
        }
    }

    private List<TableDefinition> tableDefinitions(Connection connection) throws SQLException {
        List<TableDefinition> definitions = new ArrayList<>();
        for (String table : TABLES) {
            List<ColumnDefinition> columns = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select * from " + quote(table) + " where 1 = 0")) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(new ColumnDefinition(metadata.getColumnLabel(index).toLowerCase(Locale.ROOT),
                            metadata.getColumnType(index), metadata.getColumnTypeName(index)));
                }
            }
            definitions.add(new TableDefinition(table, columns, countRows(connection, table)));
        }
        return definitions;
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + quote(table))) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void writeTable(ZipOutputStream zip, Connection connection, TableDefinition definition)
            throws SQLException, IOException {
        zip.putNextEntry(new ZipEntry("data/" + definition.name() + ".json"));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartArray();
        String order = definition.columns().stream().anyMatch(column -> column.name().equals("id"))
                ? " order by " + quote("id")
                : " order by " + definition.columns().stream().map(column -> quote(column.name()))
                .reduce((a, b) -> a + "," + b).orElse("1");
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select * from " + quote(definition.name()) + order)) {
            while (resultSet.next()) {
                generator.writeStartArray();
                for (int index = 0; index < definition.columns().size(); index++) {
                    writeValue(generator, resultSet, index + 1, definition.columns().get(index).jdbcType());
                }
                generator.writeEndArray();
            }
        }
        generator.writeEndArray();
        generator.close();
        zip.closeEntry();
    }

    private void writeValue(JsonGenerator generator, ResultSet resultSet, int index, int jdbcType)
            throws SQLException, IOException {
        Object value = resultSet.getObject(index);
        if (value == null) {
            generator.writeNull();
            return;
        }
        if (isBinary(jdbcType)) {
            generator.writeString(Base64.getEncoder().encodeToString(resultSet.getBytes(index)));
        } else if (jdbcType == Types.BOOLEAN || jdbcType == Types.BIT) {
            generator.writeString(resultSet.getBoolean(index) ? "true" : "false");
        } else {
            generator.writeString(resultSet.getString(index));
        }
    }

    private void writeJsonEntry(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        objectMapper.writeValue(generator, value);
        generator.close();
        zip.closeEntry();
    }

    private void writeDirectory(ZipOutputStream zip, Path source, String prefix) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path)).sorted().toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(source)) {
                    continue;
                }
                String relative = source.relativize(normalized).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(prefix + relative));
                Files.copy(normalized, zip);
                zip.closeEntry();
            }
        }
    }

    private void extractArchive(MultipartFile archive, Path target) throws IOException {
        long total = 0;
        int entries = 0;
        Set<String> names = new HashSet<>();
        byte[] buffer = new byte[8192];
        try (InputStream input = new BufferedInputStream(archive.getInputStream());
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES || !names.add(entry.getName())) {
                    throw new DomainException("El ZIP contiene demasiados archivos o entradas duplicadas.");
                }
                String name = entry.getName().replace('\\', '/');
                if (!isAllowedEntry(name)) {
                    throw new DomainException("El ZIP contiene una ruta no permitida: " + name);
                }
                Path output = target.resolve(name).normalize();
                if (!output.startsWith(target)) {
                    throw new DomainException("El ZIP contiene una ruta inválida.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(output))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_UNCOMPRESSED_SIZE) {
                                throw new DomainException("El contenido del respaldo supera el límite permitido.");
                            }
                            file.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private boolean isAllowedEntry(String name) {
        if (name.equals("manifest.json")) {
            return true;
        }
        if (name.startsWith("images/products/") || name.startsWith("images/catalog/")) {
            return true;
        }
        if (name.startsWith("data/") && name.endsWith(".json")) {
            String table = name.substring("data/".length(), name.length() - ".json".length());
            return TABLES.contains(table);
        }
        return false;
    }

    private Manifest readAndValidateManifest(Path extracted) throws IOException {
        Path path = extracted.resolve("manifest.json");
        if (!Files.isRegularFile(path)) {
            throw new DomainException("El ZIP no contiene manifest.json.");
        }
        Manifest manifest;
        try {
            manifest = objectMapper.readValue(path.toFile(), Manifest.class);
        } catch (Exception ex) {
            throw new DomainException("El manifiesto del respaldo no es válido.");
        }
        if (!FORMAT.equals(manifest.format()) || manifest.formatVersion() != FORMAT_VERSION) {
            throw new DomainException("El archivo no es un respaldo compatible de Tienda POS.");
        }
        if (manifest.schemaVersion() != SCHEMA_VERSION) {
            throw new DomainException("El respaldo usa una versión de base de datos incompatible.");
        }
        return manifest;
    }

    private void validateManifestTables(Manifest manifest, Map<String, TableDefinition> current, Path extracted) {
        if (manifest.tables() == null || manifest.tables().size() != TABLES.size()) {
            throw new DomainException("El respaldo no contiene todas las tablas requeridas.");
        }
        Set<String> found = new HashSet<>();
        for (TableDefinition imported : manifest.tables()) {
            if (!found.add(imported.name()) || !TABLES.contains(imported.name())) {
                throw new DomainException("El respaldo contiene tablas inválidas o duplicadas.");
            }
            TableDefinition expected = current.get(imported.name());
            List<String> importedColumns = imported.columns().stream().map(ColumnDefinition::name).toList();
            List<String> expectedColumns = expected.columns().stream().map(ColumnDefinition::name).toList();
            if (!importedColumns.equals(expectedColumns)) {
                throw new DomainException("La estructura de la tabla " + imported.name() + " no es compatible.");
            }
            if (!Files.isRegularFile(extracted.resolve("data/" + imported.name() + ".json"))) {
                throw new DomainException("Faltan los datos de la tabla " + imported.name() + ".");
            }
        }
        if (!found.equals(new HashSet<>(TABLES))) {
            throw new DomainException("El respaldo está incompleto.");
        }
    }

    private void clearTables(Connection connection) throws SQLException {
        List<String> reversed = new ArrayList<>(TABLES);
        Collections.reverse(reversed);
        try (Statement statement = connection.createStatement()) {
            for (String table : reversed) {
                statement.executeUpdate("delete from " + quote(table));
            }
        }
    }

    private void importTables(Connection connection, Manifest manifest,
                              Map<String, TableDefinition> current, Path extracted) throws IOException, SQLException {
        Map<String, TableDefinition> imported = definitionsByName(manifest.tables());
        for (String table : TABLES) {
            TableDefinition definition = current.get(table);
            Path data = extracted.resolve("data/" + table + ".json");
            String columns = definition.columns().stream().map(column -> quote(column.name()))
                    .reduce((a, b) -> a + "," + b).orElseThrow();
            String placeholders = String.join(",", Collections.nCopies(definition.columns().size(), "?"));
            String sql = "insert into " + quote(table) + " (" + columns + ") values (" + placeholders + ")";
            long importedRows = 0;
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 JsonParser parser = objectMapper.getFactory().createParser(data.toFile())) {
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new DomainException("Los datos de " + table + " no son válidos.");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode row = objectMapper.readTree(parser);
                    if (!row.isArray() || row.size() != definition.columns().size()) {
                        throw new DomainException("Una fila de " + table + " no es compatible.");
                    }
                    for (int index = 0; index < definition.columns().size(); index++) {
                        bindValue(statement, index + 1, definition.columns().get(index).jdbcType(), row.get(index));
                    }
                    statement.addBatch();
                    importedRows++;
                    if (importedRows % BATCH_SIZE == 0) {
                        statement.executeBatch();
                    }
                }
                statement.executeBatch();
            }
            if (importedRows != imported.get(table).rowCount()) {
                throw new DomainException("El número de registros de " + table + " no coincide con el manifiesto.");
            }
        }
    }

    private void bindValue(PreparedStatement statement, int index, int jdbcType, JsonNode node) throws SQLException {
        if (node == null || node.isNull()) {
            statement.setNull(index, jdbcType);
            return;
        }
        String value = node.asText();
        switch (jdbcType) {
            case Types.BIGINT -> statement.setLong(index, Long.parseLong(value));
            case Types.INTEGER -> statement.setInt(index, Integer.parseInt(value));
            case Types.SMALLINT -> statement.setShort(index, Short.parseShort(value));
            case Types.TINYINT -> statement.setByte(index, Byte.parseByte(value));
            case Types.DECIMAL, Types.NUMERIC -> statement.setBigDecimal(index, new BigDecimal(value));
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, Boolean.parseBoolean(value) || "1".equals(value));
            case Types.DATE -> statement.setDate(index, Date.valueOf(value.substring(0, 10)));
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> statement.setTime(index, Time.valueOf(value));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> statement.setTimestamp(index, Timestamp.valueOf(value.replace('T', ' ')));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> statement.setBytes(index, Base64.getDecoder().decode(value));
            default -> statement.setString(index, value);
        }
    }

    private Map<String, TableDefinition> definitionsByName(List<TableDefinition> definitions) {
        Map<String, TableDefinition> result = new LinkedHashMap<>();
        for (TableDefinition definition : definitions) {
            result.put(definition.name(), definition);
        }
        return result;
    }

    private void validateImageTargets() {
        if (productImages.equals(catalogImages) || productImages.startsWith(catalogImages) || catalogImages.startsWith(productImages)) {
            throw new DomainException("Los directorios de imágenes están configurados de forma incompatible.");
        }
    }

    private DirectorySwap swapDirectory(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        String token = UUID.randomUUID().toString();
        Path staged = parent.resolve("." + target.getFileName() + ".restore-" + token);
        Path previous = parent.resolve("." + target.getFileName() + ".backup-" + token);
        copyDirectory(source, staged);
        boolean hadPrevious = Files.exists(target);
        try {
            if (hadPrevious) {
                moveDirectory(target, previous);
            }
            moveDirectory(staged, target);
            return new DirectorySwap(target, previous, hadPrevious);
        } catch (IOException ex) {
            deleteRecursively(staged);
            if (hadPrevious && Files.exists(previous) && !Files.exists(target)) {
                moveDirectory(previous, target);
            }
            throw ex;
        }
    }

    private void registerSwapCompletion(List<DirectorySwap> swaps) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("La importación requiere una transacción activa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    swaps.forEach(swap -> deleteRecursively(swap.previous()));
                } else {
                    restoreSwaps(swaps);
                }
            }
        });
    }

    private void restoreSwaps(List<DirectorySwap> swaps) {
        List<DirectorySwap> reversed = new ArrayList<>(swaps);
        Collections.reverse(reversed);
        for (DirectorySwap swap : reversed) {
            try {
                deleteRecursively(swap.target());
                if (swap.hadPrevious() && Files.exists(swap.previous())) {
                    moveDirectory(swap.previous(), swap.target());
                }
            } catch (Exception ex) {
                log.error("Could not restore image directory {}", swap.target(), ex);
            }
        }
        swaps.clear();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path destination = target.resolve(source.relativize(path).toString()).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Invalid image path");
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private long countFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path)).count();
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted(Collections.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        } catch (IOException ex) {
            log.warn("Could not remove temporary backup path {}", path, ex);
        }
    }

    private String quote(String identifier) {
        if (!identifier.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return "`" + identifier + "`";
    }

    private boolean isBinary(int jdbcType) {
        return jdbcType == Types.BINARY || jdbcType == Types.VARBINARY
                || jdbcType == Types.LONGVARBINARY || jdbcType == Types.BLOB;
    }

    public record BackupInfo(int tables, long records, long productImages, long catalogImages) { }

    public record Manifest(String format, int formatVersion, int schemaVersion, String exportedAt,
                           List<TableDefinition> tables, long productImages, long catalogImages) { }

    public record TableDefinition(String name, List<ColumnDefinition> columns, long rowCount) { }

    public record ColumnDefinition(String name, int jdbcType, String typeName) { }

    private record DirectorySwap(Path target, Path previous, boolean hadPrevious) { }
}
