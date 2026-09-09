package com.tienda.pos.commercial;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantMigrationTest {

    enum Database {
        MYSQL_8, MARIADB;

        JdbcDatabaseContainer<?> container() {
            JdbcDatabaseContainer<?> database = this == MYSQL_8
                    ? new MySQLContainer<>("mysql:8.0.36")
                        .withUrlParam("allowPublicKeyRetrieval", "true").withUrlParam("useSSL", "false")
                    : new MariaDBContainer<>("mariadb:11.4.5");
            return database.withDatabaseName("migration_test")
                    .withUsername("migration_test").withPassword("container-only-password")
                    .withReuse(false);
        }
    }

    @ParameterizedTest(name = "{0}: empty schema -> every real migration SUCCESS")
    @EnumSource(Database.class)
    void migratesEmptySchemaThroughLatestRealMigration(Database engine) throws Exception {
        // No Spring context, external datasource, init script, baseline or optional Docker skip.
        try (JdbcDatabaseContainer<?> database = engine.container()) {
            database.start();
            try (Connection connection = connect(database)) {
                assertThat(count(connection, "select count(*) from information_schema.tables where table_schema = database()"))
                        .as("Schema must be empty before Flyway, including its history").isZero();
                System.out.println(engine + ": EMPTY_SCHEMA confirmed; server=" + connection.getMetaData().getDatabaseProductVersion());

                Flyway flyway = configuration(database).load();
                List<String> versions = Arrays.stream(flyway.info().pending())
                        .map(info -> info.getVersion().getVersion()).toList();
                assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7", "8", "9");
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(versions.size());
                assertSuccessfulHistory(flyway, connection, versions, engine);
                assertInventoryConstraints(connection);
                assertAllForeignKeysHaveSupportingIndexes(connection);
                assertNoDuplicateIndexes(connection);
                assertThat(flyway.migrate().migrationsExecuted).as("Second startup is a no-op").isZero();
            }
        }
    }

    @ParameterizedTest(name = "{0}: reproduce FK failure at V8 and upgrade populated schema")
    @EnumSource(Database.class)
    void upgradesV8WithoutLosingForeignKeysStockOrLegacyLocations(Database engine) throws Exception {
        try (JdbcDatabaseContainer<?> database = engine.container()) {
            database.start();
            configuration(database).target("7").load().migrate();
            try (Connection connection = connect(database)) {
                execute(connection, """
                        insert into product(id, version, created_at, updated_at, code, name, purchase_cost,
                            sale_price, current_stock, minimum_stock, unit, active)
                        values (100, 0, current_timestamp, current_timestamp, 'LEGACY', 'Legacy stock',
                            14.80, 20.00, 25.00, 2.00, 'PIEZA', true)
                        """);
                configuration(database).target("8").load().migrate();
                List<String> originalForeignKeys = foreignKeys(connection);

                // The exact original failing statement, against real V8, must still be rejected.
                assertThatThrownBy(() -> execute(connection,
                        "alter table inventory_stock drop index uk_inventory_stock_product_warehouse"))
                        .isInstanceOfSatisfying(SQLException.class, failure -> {
                            assertThat(failure.getErrorCode()).isEqualTo(1553);
                            assertThat(failure.getMessage()).contains("uk_inventory_stock_product_warehouse");
                        });
                System.out.println(engine + ": reproduced error 1553 for the original DROP INDEX");

                execute(connection, """
                        insert into branch(version, created_at, updated_at, business_id, name, active)
                        select 0, current_timestamp, current_timestamp, id, 'Second branch', true from business
                        """);
                execute(connection, """
                        insert into warehouse(version, created_at, updated_at, branch_id, name, main_warehouse, active)
                        select 0, current_timestamp, current_timestamp, id, 'Extra warehouse', false, true from branch
                        """);
                execute(connection, """
                        insert into cash_register(version, created_at, updated_at, branch_id, name, active)
                        select 0, current_timestamp, current_timestamp, id, 'Extra register', true from branch
                        """);

                Flyway flyway = configuration(database).load();
                flyway.migrate();
                flyway.validate();
                assertThat(flyway.info().all()).allSatisfy(info -> assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
                assertThat(foreignKeys(connection)).containsAll(originalForeignKeys);
                assertInventoryConstraints(connection);
                assertAllForeignKeysHaveSupportingIndexes(connection);
                assertNoDuplicateIndexes(connection);
                assertThat(count(connection, """
                        select count(*) from inventory_stock s join product p on p.id = s.product_id
                        where p.code = 'LEGACY' and s.tenant_id = p.tenant_id and s.quantity = 25.00
                            and s.minimum_stock = 2.00 and p.purchase_cost = 14.80
                        """)).isEqualTo(1);
                assertThat(count(connection, "select count(*) from branch")).isEqualTo(2);
                assertThat(count(connection, "select count(*) from warehouse")).isEqualTo(3);
                assertThat(count(connection, "select count(*) from cash_register")).isEqualTo(3);
                for (String table : List.of("branch", "warehouse", "cash_register")) {
                    assertThat(count(connection, "select count(*) from " + table + " where tenant_id is null or code is null"))
                            .as("Backfilled " + table).isZero();
                }
                assertThat(count(connection, "select count(*) from branch where code = 'MATRIZ'")).isEqualTo(1);
                assertThat(count(connection, "select count(*) from warehouse where code = 'PRINCIPAL'")).isEqualTo(2);
                assertThat(count(connection, "select count(*) from cash_register where code = 'CAJA01'")).isEqualTo(2);

                assertThatThrownBy(() -> execute(connection, """
                        insert into inventory_stock(version, created_at, updated_at, tenant_id, product_id, warehouse_id, quantity, minimum_stock)
                        select 0, current_timestamp, current_timestamp, tenant_id, product_id, warehouse_id, 1, 0 from inventory_stock
                        """)).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(1062));
                assertThatThrownBy(() -> execute(connection, "update inventory_stock set product_id = 999999"))
                        .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(1452));
                System.out.println(engine + ": V8 -> latest SUCCESS; all original foreign keys and inventory values preserved");
            }
        }
    }

    private static FluentConfiguration configuration(JdbcDatabaseContainer<?> database) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true);
    }

    private static Connection connect(JdbcDatabaseContainer<?> database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static void assertSuccessfulHistory(Flyway flyway, Connection connection, List<String> versions,
                                                Database engine) throws SQLException {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all()).hasSize(versions.size());
        for (MigrationInfo info : flyway.info().all()) {
            assertThat(info.getState()).as(info.getScript()).isEqualTo(MigrationState.SUCCESS);
            System.out.println(engine + ": V" + info.getVersion() + " SUCCESS (" + info.getScript() + ")");
        }
        assertThat(count(connection, "select count(*) from flyway_schema_history where success = false")).isZero();
        assertThat(count(connection, "select count(*) from flyway_schema_history where success = true"))
                .isEqualTo(versions.size());
    }

    private static void assertInventoryConstraints(Connection connection) throws SQLException {
        Map<String, Map<Integer, String>> indexes = new TreeMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("show index from inventory_stock")) {
            while (rows.next()) {
                indexes.computeIfAbsent(rows.getString("Key_name"), key -> new TreeMap<>())
                        .put(rows.getInt("Seq_in_index"), rows.getString("Column_name"));
                if (rows.getString("Key_name").equals("uk_inventory_stock_tenant_product_warehouse")) {
                    assertThat(rows.getBoolean("Non_unique")).isFalse();
                }
            }
        }
        System.out.println("SHOW INDEX FROM inventory_stock: " + indexes);
        assertThat(indexes).containsKeys("idx_inventory_stock_product", "uk_inventory_stock_tenant_product_warehouse")
                .doesNotContainKey("uk_inventory_stock_product_warehouse");
        assertThat(indexes.get("idx_inventory_stock_product").values()).containsExactly("product_id");
        assertThat(indexes.get("uk_inventory_stock_tenant_product_warehouse").values())
                .containsExactly("tenant_id", "product_id", "warehouse_id");
        assertThat(foreignKeys(connection)).contains("inventory_stock.fk_inventory_stock_product:product_id->product.id");
    }

    private static List<String> foreignKeys(Connection connection) throws SQLException {
        return strings(connection, """
                select concat(table_name, '.', constraint_name, ':', column_name, '->', referenced_table_name, '.', referenced_column_name)
                from information_schema.key_column_usage
                where table_schema = database() and referenced_table_name is not null
                order by table_name, constraint_name, ordinal_position
                """);
    }

    private static void assertAllForeignKeysHaveSupportingIndexes(Connection connection) throws SQLException {
        assertThat(strings(connection, """
                select concat(k.table_name, '.', k.constraint_name)
                from information_schema.key_column_usage k
                where k.table_schema = database() and k.referenced_table_name is not null
                    and not exists (
                        select 1 from information_schema.statistics i
                        where i.table_schema = k.table_schema and i.table_name = k.table_name
                            and i.column_name = k.column_name and i.seq_in_index = k.ordinal_position)
                """)).as("All migration foreign keys retain a leading supporting index").isEmpty();
    }

    private static void assertNoDuplicateIndexes(Connection connection) throws SQLException {
        assertThat(strings(connection, """
                select concat(table_name, ':', columns_list) from (
                    select table_name, index_name, non_unique,
                        group_concat(column_name order by seq_in_index) as columns_list
                    from information_schema.statistics where table_schema = database()
                    group by table_name, index_name, non_unique
                ) indexes_by_columns
                group by table_name, non_unique, columns_list having count(*) > 1
                """)).as("No duplicate indexes with identical columns and uniqueness").isEmpty();
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
