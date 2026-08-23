package com.tienda.pos.setup;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
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
                long userId = insertAdmin(connection, form);
                long adminRoleId = findRoleId(connection, "ROLE_ADMIN");
                try (PreparedStatement ps = connection.prepareStatement("insert into user_roles(user_id, role_id) values (?, ?)")) {
                    ps.setLong(1, userId);
                    ps.setLong(2, adminRoleId);
                    ps.executeUpdate();
                }
                upsertBusinessSettings(connection, form);
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

    private long insertAdmin(Connection connection, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into app_user(username, password_hash, first_name, last_name, email, active, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, true, 0, current_timestamp, current_timestamp)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, form.getAdminUsername().trim());
            ps.setString(2, passwordEncoder.encode(form.getAdminPassword()));
            ps.setString(3, form.getAdminFirstName().trim());
            ps.setString(4, form.getAdminLastName().trim());
            ps.setString(5, blankToNull(form.getAdminEmail()));
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

    private void upsertBusinessSettings(Connection connection, SetupForm form) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                update business_settings
                set store_name = ?, address = ?, phone = ?, tax_id = ?, currency = ?, currency_symbol = ?, timezone = ?, updated_at = current_timestamp
                where id = 1
                """)) {
            ps.setString(1, form.getStoreName());
            ps.setString(2, blankToNull(form.getStoreAddress()));
            ps.setString(3, blankToNull(form.getStorePhone()));
            ps.setString(4, blankToNull(form.getTaxId()));
            ps.setString(5, form.getCurrency());
            ps.setString(6, form.getCurrencySymbol());
            ps.setString(7, form.getTimezone());
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
