package com.tienda.pos.commercial;

import com.tienda.pos.setup.SetupForm;
import com.tienda.pos.setup.SetupService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.File;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.*;

public class PlatformMigrationTest {
    @TempDir Path directory;
    enum Existing { NO_ROLE, ROLE_ONLY, PLATFORM_EXISTS }
    static Stream<Arguments> upgrades() {
        return Arrays.stream(TenantMigrationTest.Database.values())
                .flatMap(db -> Arrays.stream(Existing.values()).map(state -> Arguments.of(db, state)));
    }

    @ParameterizedTest @EnumSource(TenantMigrationTest.Database.class)
    void emptySchemaThroughV10ThenRealSetup(TenantMigrationTest.Database engine) throws Exception {
        try (JdbcDatabaseContainer<?> db = engine.container()) {
            db.start();
            try (Connection c = connect(db)) {
                assertThat(count(c,"select count(*) from information_schema.tables where table_schema=database()")).isZero();
                Flyway flyway = flyway(db);
                assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(10);
                assertSuccess(flyway);
                assertThat(count(c,"select count(*) from app_user")).isZero();
                assertThat(count(c,"select count(*) from user_roles")).isZero();
                assertThat(count(c,"select count(*) from role where name='ROLE_PLATFORM_ADMIN'")).isEqualTo(1);
                runSetupInCleanProcess(db,engine);
                assertThat(count(c,"select count(*) from tenant")).isEqualTo(1);
                assertThat(count(c,"select count(*) from app_user")).isEqualTo(1);
                assertThat(strings(c,"select r.name from role r join user_roles ur on r.id=ur.role_id order by r.name"))
                        .containsExactly("ROLE_ADMIN","ROLE_PLATFORM_ADMIN");
                assertThat(count(c,"select count(*) from business")).isEqualTo(1);
                assertThat(count(c,"select count(*) from branch")).isEqualTo(1);
                assertThat(count(c,"select count(*) from warehouse")).isEqualTo(1);
                assertThat(count(c,"select count(*) from cash_register")).isEqualTo(1);
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                System.out.println(engine + ": EMPTY -> V1..V10 SUCCESS -> REAL SETUP PLATFORM ADMIN SUCCESS");
            }
        }
    }

    @ParameterizedTest @MethodSource("upgrades")
    void upgradesV9DeterministicallyAndPreservesEveryOperationalRow(TenantMigrationTest.Database engine, Existing state) throws Exception {
        try (JdbcDatabaseContainer<?> db=engine.container()) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                    .locations("classpath:db/migration").target("7").load().migrate();
            try(Connection c=connect(db)) {
                execute(c,"""
                        insert into app_user(id,version,created_at,updated_at,username,password_hash,first_name,last_name,active)
                        values (10,0,current_timestamp,current_timestamp,'cashier','hash','Cash','User',true),
                        (20,0,current_timestamp,current_timestamp,'first-admin','hash','First','Admin',true),
                        (30,0,current_timestamp,current_timestamp,'second-admin','hash','Second','Admin',true)
                        """);
                execute(c,"insert into user_roles(user_id,role_id) select 10,id from role where name='ROLE_CAJERO'");
                execute(c,"insert into user_roles(user_id,role_id) select 20,id from role where name='ROLE_ADMIN'");
                execute(c,"insert into user_roles(user_id,role_id) select 30,id from role where name='ROLE_ADMIN'");
                execute(c,"""
                        insert into product(id,version,created_at,updated_at,code,name,purchase_cost,sale_price,current_stock,minimum_stock,unit,active)
                        values (100,0,current_timestamp,current_timestamp,'LEGACY','Legacy inventory',14.80,20,25,2,'PIEZA',true)
                        """);
                execute(c,"""
                        insert into sale(id,version,created_at,updated_at,folio,sale_date,cashier_id,subtotal,discount,tax,total,status)
                        values (100,0,current_timestamp,current_timestamp,'LEGACY-SALE',current_timestamp,20,103,0,0,103,'COMPLETED')
                        """);
                Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                        .locations("classpath:db/migration").target("9").load().migrate();
                if(state!=Existing.NO_ROLE)
                    execute(c,"insert into role(version,created_at,updated_at,name) values(0,current_timestamp,current_timestamp,'ROLE_PLATFORM_ADMIN')");
                if(state==Existing.PLATFORM_EXISTS)
                    execute(c,"insert into user_roles(user_id,role_id) select 30,id from role where name='ROLE_PLATFORM_ADMIN'");
                Map<String,List<String>> before=snapshot(c);
                Flyway latest=flyway(db);
                assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
                assertSuccess(latest);
                assertThat(snapshot(c)).isEqualTo(before);
                assertThat(strings(c,"select cast(ur.user_id as char) from user_roles ur join role r on r.id=ur.role_id where r.name='ROLE_PLATFORM_ADMIN'"))
                        .containsExactly(state==Existing.PLATFORM_EXISTS?"30":"20");
                assertThat(strings(c,"select cast(ur.user_id as char) from user_roles ur join role r on r.id=ur.role_id where r.name='ROLE_ADMIN' order by ur.user_id"))
                        .containsExactly("20","30");
                assertThat(count(c,"select count(*) from role where name='ROLE_PLATFORM_ADMIN'")).isEqualTo(1);
                assertThat(latest.migrate().migrationsExecuted).isZero();
                System.out.println(engine+": V9 -> V10 "+state+" SUCCESS; all operational rows unchanged");
            }
        }
    }

    private void runSetupInCleanProcess(JdbcDatabaseContainer<?> db,TenantMigrationTest.Database engine) throws Exception {
        Path output=directory.resolve("setup.log");
        assertThat(directory.resolve("config/application.yml")).doesNotExist();
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        String cp=Arrays.stream(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")).split(Pattern.quote(File.pathSeparator)))
                .map(entry->Path.of(entry).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
        ProcessBuilder builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),
                "-cp",cp,PlatformMigrationTest.class.getName(),engine==TenantMigrationTest.Database.MYSQL_8?"MySQL":"MariaDB",
                db.getHost(),db.getMappedPort(3306).toString(),db.getDatabaseName(),db.getUsername(),db.getPassword())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(name-> {
            String key=name.toUpperCase(Locale.ROOT);
            return key.startsWith("SPRING_")||key.startsWith("TIENDA_")||List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS").contains(key);
        });
        Process process=builder.start();
        try {
            boolean exited=process.waitFor(90,TimeUnit.SECONDS);
            if(!exited) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); }
            String log=Files.readString(output);
            assertThat(exited).withFailMessage(log).isTrue();
            assertThat(process.exitValue()).withFailMessage(log).isZero();
            assertThat(log).contains("PLATFORM_SETUP_OK");
            assertThat(directory.resolve("config/application.yml")).exists();
        } finally {
            if(process.isAlive()) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); }
        }
    }

    public static void main(String[] args) throws Exception {
        assertThat(Path.of("config/application.yml")).doesNotExist();
        SetupForm form=new SetupForm(); form.setEngine(args[0]); form.setHost(args[1]); form.setPort(Integer.parseInt(args[2]));
        form.setDatabaseName(args[3]); form.setDatabaseUser(args[4]); form.setDatabasePassword(args[5]);
        form.setAdditionalParams("allowPublicKeyRetrieval=true&useSSL=false");
        form.setAdminFirstName("Initial"); form.setAdminLastName("Admin"); form.setAdminUsername("initial-admin");
        form.setAdminPassword("Setup-only-493!"); form.setAdminPasswordConfirm("Setup-only-493!");
        form.setStoreName("Clean setup");
        new SetupService().install(form);
        System.out.println("PLATFORM_SETUP_OK");
    }

    private static Map<String,List<String>> snapshot(Connection c) throws SQLException {
        Map<String,List<String>> snapshot=new TreeMap<>();
        for(String table:strings(c,"select table_name from information_schema.tables where table_schema=database() order by table_name")) {
            if(List.of("role","user_roles","flyway_schema_history").contains(table)) continue;
            List<String> rows=new ArrayList<>();
            try(Statement statement=c.createStatement();ResultSet rs=statement.executeQuery("select * from "+table+" order by id")) {
                while(rs.next()) {
                    List<String> cells=new ArrayList<>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++) cells.add(rs.getString(i));
                    rows.add(cells.toString());
                }
            }
            snapshot.put(table,rows);
        }
        return snapshot;
    }
    private static void assertSuccess(Flyway flyway) {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Arrays.stream(flyway.info().all()).map(i->i.getVersion().getVersion())).contains("1","2","3","4","5","6","7","8","9","10");
        assertThat(flyway.info().all()).allSatisfy(i->assertThat(i.getState()).as(i.getScript()).isEqualTo(MigrationState.SUCCESS));
    }
    private static Flyway flyway(JdbcDatabaseContainer<?> db) {
        return Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).locations("classpath:db/migration").baselineOnMigrate(false).load();
    }
    private static Connection connect(JdbcDatabaseContainer<?> db) throws SQLException { return DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword()); }
    private static long count(Connection c,String sql) throws SQLException { try(Statement s=c.createStatement();ResultSet rs=s.executeQuery(sql)) { rs.next(); return rs.getLong(1); } }
    private static void execute(Connection c,String sql) throws SQLException { try(Statement s=c.createStatement()) { s.executeUpdate(sql); } }
    private static List<String> strings(Connection c,String sql) throws SQLException {
        List<String> rows=new ArrayList<>(); try(Statement s=c.createStatement();ResultSet rs=s.executeQuery(sql)) { while(rs.next())rows.add(rs.getString(1)); } return rows;
    }
}
