package com.tienda.pos.commercial;

import org.testcontainers.containers.MySQLContainer;

import java.io.File;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Executed after package: the application under test is the actual executable JAR. */
public final class PackagedNormalModeSmoke {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path directory = Path.of(args[1]).toAbsolutePath();
        assertThat(jar).isRegularFile();
        Files.createDirectories(directory);
        try (var entries = Files.list(directory)) { assertThat(entries.toList()).isEmpty(); }
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
        try (var db = new MySQLContainer<>("mysql:8.0.36")) {
            db.start();
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("select count(*) from information_schema.tables where table_schema=database()")) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
            Path setupLog = directory.resolve("setup.log");
            Process setup = process(directory, setupLog, executable, "-cp", classpath, PlatformMigrationTest.class.getName(),
                    "MySQL", db.getHost(), db.getMappedPort(3306).toString(), db.getDatabaseName(), db.getUsername(), db.getPassword());
            try {
                assertThat(setup.waitFor(90, TimeUnit.SECONDS)).as("setup timeout").isTrue();
                assertThat(setup.exitValue()).withFailMessage(Files.readString(setupLog)).isZero();
            } finally { stop(setup); }
            assertThat(directory.resolve("config/application.yml")).isRegularFile();
            Path log = directory.resolve("startup.log");
            Process application = process(directory, log, executable, "-jar", jar.toString(),
                    "--server.address=127.0.0.1", "--server.port=0");
            try {
                int port = awaitPort(application, log);
                URI base = URI.create("http://127.0.0.1:" + port);
                HttpClient anonymous = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                for (String method : List.of("HEAD", "GET")) {
                    var root = anonymous.send(HttpRequest.newBuilder(base.resolve("/"))
                            .method(method, HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(15)).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertThat(root.statusCode()).isEqualTo(302);
                    assertThat(root.headers().firstValue("Location")).contains("/admin/login");
                    assertThat(root.body()).doesNotContain("catalog/index", "catalog-grid", "Clean setup");
                    Files.writeString(directory.resolve("root-" + method + ".txt"),
                            "status=" + root.statusCode() + "\nLocation=" + root.headers().firstValue("Location").orElse("") + "\n" + root.body());
                }
                CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
                var login = get(browser, base.resolve("/admin/login"));
                assertThat(login.statusCode()).isEqualTo(200);
                assertThat(login.body()).contains("Iniciar sesi", "username", "password").doesNotContain("catalog/index");
                for (String path : List.of("/producto/1", "/catalog/products")) {
                    assertThat(get(anonymous, base.resolve(path)).statusCode()).isEqualTo(404);
                }
                var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
                assertThat(token.find()).as("rendered CSRF token").isTrue();
                String beforeLogin = sessionId(cookies);
                String form = "username=initial-admin&password=" + URLEncoder.encode("Setup-only-493!", java.nio.charset.StandardCharsets.UTF_8)
                        + "&_csrf=" + URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
                var authenticated = browser.send(HttpRequest.newBuilder(base.resolve("/admin/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(authenticated.statusCode()).isEqualTo(302);
                assertThat(authenticated.headers().firstValue("Location")).contains("/platform/tenants");
                String oldSession = sessionId(cookies);
                assertThat(oldSession).isNotBlank().isNotEqualTo(beforeLogin);
                assertThat(get(browser, base.resolve("/platform/tenants")).statusCode()).isEqualTo(200);
                assertThat(get(browser, base.resolve("/")).headers().firstValue("Location")).contains("/platform/tenants");
                // Out-of-band DB changes exercise the request-time backstop in a real servlet container.
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var statement = connection.createStatement()) {
                    assertThat(statement.executeUpdate("update app_user set active=false where username='initial-admin'")).isEqualTo(1);
                }
                var expired = get(browser, base.resolve("/platform/tenants"));
                assertThat(expired.statusCode()).isEqualTo(302);
                assertThat(expired.headers().firstValue("Location")).contains("/admin/login?expired");
                assertThat(expired.headers().allValues("Set-Cookie"))
                        .anySatisfy(cookie -> assertThat(cookie).contains("JSESSIONID=", "Max-Age=0"));
                for (String path : List.of("/admin", "/platform/tenants")) {
                    var replay = anonymous.send(HttpRequest.newBuilder(base.resolve(path))
                            .header("Cookie", "JSESSIONID=" + oldSession).GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertThat(replay.statusCode()).isEqualTo(302);
                    assertThat(replay.headers().firstValue("Location").orElseThrow()).endsWith("/admin/login");
                }
                String evidence = "NORMAL_JAR_OK: isolated MySQL 8; empty schema -> real setup V1..V10; "
                        + "HEAD/GET /=302 /admin/login; login=200; retired storefront=404; "
                        + "real login + session fixation + suspension invalidation + old cookie replay rejected";
                Files.writeString(directory.resolve("result.txt"), evidence + "\n");
                System.out.println(evidence);
            } finally { stop(application); }
        }
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String sessionId(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .map(HttpCookie::getValue).findFirst().orElseThrow();
    }

    private static int awaitPort(Process process, Path log) throws Exception {
        for (int attempt = 0; attempt < 90; attempt++) {
            String text = Files.readString(log);
            assertThat(process.isAlive()).withFailMessage(text).isTrue();
            var match = Pattern.compile("Tomcat started on port (\\d+)").matcher(text);
            if (match.find()) return Integer.parseInt(match.group(1));
            Thread.sleep(1000);
        }
        throw new AssertionError("JAR startup timeout: " + Files.readString(log));
    }

    private static Process process(Path directory, Path log, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().keySet().removeIf(name -> {
            String key = name.toUpperCase(Locale.ROOT);
            return key.startsWith("SPRING_") || key.startsWith("TIENDA_")
                    || List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS").contains(key);
        });
        return builder.start();
    }

    private static void stop(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
