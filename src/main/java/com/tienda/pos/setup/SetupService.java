package com.tienda.pos.setup;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public void testConnection(SetupForm form) throws SQLException {
        try (Connection connection = DriverManager.getConnection(form.jdbcUrl(), form.getDatabaseUser(), form.getDatabasePassword())) {
            if (!connection.isValid(5)) {
                throw new SQLException("La conexión no respondió correctamente.");
            }
        }
    }

    public void install(SetupForm form) throws SQLException, IOException {
        if (!form.passwordsMatch()) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden.");
        }
        testConnection(form);
        Flyway.configure()
                .dataSource(form.jdbcUrl(), form.getDatabaseUser(), form.getDatabasePassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(form.jdbcUrl(), form.getDatabaseUser(), form.getDatabasePassword())) {
            connection.setAutoCommit(false);
            try {
                ensureAdminDoesNotExist(connection);
                long tenantId = upsertTenant(connection, form);
                upsertBusinessSettings(connection, tenantId, form);
                upsertCommercialStructure(connection, tenantId, form);
                long userId = insertAdmin(connection, tenantId, form);
                long adminRoleId = findRoleId(connection, "ROLE_ADMIN");
                try (PreparedStatement ps = connection.prepareStatement("insert into user_roles(user_id, role_id) values (?, ?)")) {
                    ps.setLong(1, userId);
                    ps.setLong(2, adminRoleId);
                    ps.executeUpdate();
                }
                connection.commit();
                writeExternalConfig(form);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        log.info("Instalación inicial completada. Base configurada: {}", form.getDatabaseName());
    }

    private void ensureAdminDoesNotExist(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("""
                     select count(*) from app_user u
                     join user_roles ur on ur.user_id = u.id
                     join role r on r.id = ur.role_id
                     where r.name = 'ROLE_ADMIN'
                     """)) {
            rs.next();
            if (rs.getLong(1) > 0) {
                throw new IllegalStateException("Ya existe un administrador. El setup no puede ejecutarse nuevamente.");
            }
        }
    }

    private long upsertTenant(Connection connection, SetupForm form) throws SQLException {
        Long tenantId = findFirstId(connection, "tenant");
        if (tenantId == null) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    insert into tenant(version, created_at, updated_at, code, name, active)
                    values (0, current_timestamp, current_timestamp, 'default', ?, true)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, form.getStoreName());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("No fue posible crear el tenant principal.");
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                update tenant
                set name = ?, active = true, updated_at = current_timestamp
                where id = ?
                """)) {
            ps.setString(1, form.getStoreName());
            ps.setLong(2, tenantId);
            ps.executeUpdate();
        }
        return tenantId;
    }

    private long insertAdmin(Connection connection, long tenantId, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into app_user(tenant_id, username, password_hash, first_name, last_name, email, active, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, true, 0, current_timestamp, current_timestamp)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.setString(2, form.getAdminUsername().trim());
            ps.setString(3, passwordEncoder.encode(form.getAdminPassword()));
            ps.setString(4, form.getAdminFirstName().trim());
            ps.setString(5, form.getAdminLastName().trim());
            ps.setString(6, blankToNull(form.getAdminEmail()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No fue posible crear el administrador.");
    }

    private long findRoleId(Connection connection, String role) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select id from role where name = ?")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No existe el rol requerido: " + role);
    }

    private void upsertBusinessSettings(Connection connection, long tenantId, SetupForm form) throws SQLException {
        Long settingsId = findFirstId(connection, "business_settings");
        if (settingsId == null) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    insert into business_settings(tenant_id, version, created_at, updated_at, store_name, address, phone, tax_id, currency, currency_symbol, timezone, default_tax, catalog_enabled, negative_stock_allowed)
                    values (?, 0, current_timestamp, current_timestamp, ?, ?, ?, ?, ?, ?, ?, 0.00, true, false)
                    """)) {
                ps.setLong(1, tenantId);
                ps.setString(2, form.getStoreName());
                ps.setString(3, blankToNull(form.getStoreAddress()));
                ps.setString(4, blankToNull(form.getStorePhone()));
                ps.setString(5, blankToNull(form.getTaxId()));
                ps.setString(6, form.getCurrency());
                ps.setString(7, form.getCurrencySymbol());
                ps.setString(8, form.getTimezone());
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                update business_settings
                set tenant_id = ?, store_name = ?, address = ?, phone = ?, tax_id = ?, currency = ?, currency_symbol = ?, timezone = ?, updated_at = current_timestamp
                where id = ?
                """)) {
            ps.setLong(1, tenantId);
            ps.setString(2, form.getStoreName());
            ps.setString(3, blankToNull(form.getStoreAddress()));
            ps.setString(4, blankToNull(form.getStorePhone()));
            ps.setString(5, blankToNull(form.getTaxId()));
            ps.setString(6, form.getCurrency());
            ps.setString(7, form.getCurrencySymbol());
            ps.setString(8, form.getTimezone());
            ps.setLong(9, settingsId);
            ps.executeUpdate();
        }
    }

    private void upsertCommercialStructure(Connection connection, long tenantId, SetupForm form) throws SQLException {
        Long businessId = findFirstId(connection, "business");
        if (businessId == null) {
            businessId = insertBusiness(connection, tenantId, form);
        } else {
            updateBusiness(connection, businessId, tenantId, form);
        }
        updateSettingsBusiness(connection, tenantId, businessId);

        Long branchId = findFirstId(connection, "branch");
        if (branchId == null) {
            branchId = insertBranch(connection, tenantId, businessId, form);
        } else {
            updateBranch(connection, branchId, tenantId, businessId, form);
        }

        if (findFirstId(connection, "warehouse") == null) {
            insertWarehouse(connection, tenantId, branchId);
        }
        if (findFirstId(connection, "cash_register") == null) {
            insertCashRegister(connection, tenantId, branchId);
        }
    }

    private Long findFirstId(Connection connection, String tableName) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("select id from " + tableName + " order by id limit 1")) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private long insertBusiness(Connection connection, long tenantId, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into business(tenant_id, version, created_at, updated_at, name, legal_name, tax_id, phone, active)
                values (?, 0, current_timestamp, current_timestamp, ?, ?, ?, ?, true)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.setString(2, form.getStoreName());
            ps.setString(3, form.getStoreName());
            ps.setString(4, blankToNull(form.getTaxId()));
            ps.setString(5, blankToNull(form.getStorePhone()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No fue posible crear el negocio principal.");
    }

    private void updateBusiness(Connection connection, long businessId, long tenantId, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                update business
                set tenant_id = ?, name = ?, legal_name = ?, tax_id = ?, phone = ?, active = true, updated_at = current_timestamp
                where id = ?
                """)) {
            ps.setLong(1, tenantId);
            ps.setString(2, form.getStoreName());
            ps.setString(3, form.getStoreName());
            ps.setString(4, blankToNull(form.getTaxId()));
            ps.setString(5, blankToNull(form.getStorePhone()));
            ps.setLong(6, businessId);
            ps.executeUpdate();
        }
    }

    private void updateSettingsBusiness(Connection connection, long tenantId, long businessId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("update business_settings set business_id = ? where tenant_id = ?")) {
            ps.setLong(1, businessId);
            ps.setLong(2, tenantId);
            ps.executeUpdate();
        }
    }

    private long insertBranch(Connection connection, long tenantId, long businessId, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into branch(tenant_id, version, created_at, updated_at, business_id, code, name, address, phone, timezone, active)
                values (?, 0, current_timestamp, current_timestamp, ?, 'MATRIZ', 'Sucursal principal', ?, ?, ?, true)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tenantId);
            ps.setLong(2, businessId);
            ps.setString(3, blankToNull(form.getStoreAddress()));
            ps.setString(4, blankToNull(form.getStorePhone()));
            ps.setString(5, form.getTimezone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No fue posible crear la sucursal principal.");
    }

    private void updateBranch(Connection connection, long branchId, long tenantId, long businessId, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                update branch
                set tenant_id = ?, business_id = ?, code = 'MATRIZ', name = 'Sucursal principal', address = ?, phone = ?, timezone = ?, active = true, updated_at = current_timestamp
                where id = ?
                """)) {
            ps.setLong(1, tenantId);
            ps.setLong(2, businessId);
            ps.setString(3, blankToNull(form.getStoreAddress()));
            ps.setString(4, blankToNull(form.getStorePhone()));
            ps.setString(5, form.getTimezone());
            ps.setLong(6, branchId);
            ps.executeUpdate();
        }
    }

    private void insertWarehouse(Connection connection, long tenantId, long branchId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into warehouse(tenant_id, version, created_at, updated_at, branch_id, code, name, main_warehouse, active)
                values (?, 0, current_timestamp, current_timestamp, ?, 'PRINCIPAL', 'Almacén principal', true, true)
                """)) {
            ps.setLong(1, tenantId);
            ps.setLong(2, branchId);
            ps.executeUpdate();
        }
    }

    private void insertCashRegister(Connection connection, long tenantId, long branchId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into cash_register(tenant_id, version, created_at, updated_at, branch_id, code, name, active)
                values (?, 0, current_timestamp, current_timestamp, ?, 'CAJA01', 'Caja principal', true)
                """)) {
            ps.setLong(1, tenantId);
            ps.setLong(2, branchId);
            ps.executeUpdate();
        }
    }

    private void writeExternalConfig(SetupForm form) throws IOException {
        Path configDir = Path.of("config");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("application.yml");
        String safeYaml = """
                spring:
                  datasource:
                    url: "%s"
                    username: "%s"
                    password: "%s"
                  jpa:
                    hibernate:
                      ddl-auto: validate
                    open-in-view: false
                  flyway:
                    enabled: true
                tienda:
                  setup-mode: false
                  timezone: "%s"
                  currency: "%s"
                  currency-symbol: "%s"
                """.formatted(escape(form.jdbcUrl()), escape(form.getDatabaseUser()), escape(form.getDatabasePassword()),
                escape(form.getTimezone()), escape(form.getCurrency()), escape(form.getCurrencySymbol()));
        Files.writeString(configFile, safeYaml);
        try {
            configFile.toFile().setReadable(false, false);
            configFile.toFile().setReadable(true, true);
            configFile.toFile().setWritable(false, false);
            configFile.toFile().setWritable(true, true);
        } catch (SecurityException ignored) {
            log.warn("No fue posible ajustar permisos del archivo de configuración.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}