package com.tienda.pos.setup;

import com.tienda.pos.TiendaPosApplication;
import com.tienda.pos.catalog.CatalogService;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.LocalPublicTenantResolver;
import com.tienda.pos.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SetupModeStartupTest {

    @TempDir
    Path directory;

    @Test
    void cleanInstallationStartsWithoutAnyDatasource() throws Exception {
        assertThat(directory.resolve("config/application.yml")).doesNotExist();
        Path output = directory.resolve("startup-test.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(Pattern.quote(File.pathSeparator)))
                .map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
        // A separate JVM gives the real post-processor an empty working directory.
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", classpath, SetupModeStartupTest.class.getName())
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(name -> {
            String key = name.toUpperCase(Locale.ROOT);
            return key.startsWith("SPRING_") || key.startsWith("TIENDA_")
                    || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS")
                    || key.equals("_JAVA_OPTIONS");
        });
        Process process = builder.start();
        try {
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            String log = Files.readString(output);
            assertThat(exited).withFailMessage("Clean installation timed out:%n%s", log).isTrue();
            assertThat(process.exitValue()).withFailMessage("Clean installation failed:%n%s", log).isZero();
            assertThat(log).contains("SETUP_MODE_STARTUP_OK");
            assertThat(directory.resolve("config/application.yml")).doesNotExist();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        assertThat(Path.of("config/application.yml")).doesNotExist();
        SpringApplication application = new SpringApplication(TiendaPosApplication.class);
        try (ConfigurableApplicationContext context = application.run(
                "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off")) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getEnvironment().getProperty("tienda.setup-mode")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
            assertThat(context.getBean(SetupController.class)).isNotNull();
            assertThat(context.getBean(SetupService.class)).isNotNull();
            assertThat(context.getBean(SetupSecurityConfig.class)).isNotNull();
            for (Class<?> type : new Class<?>[]{CatalogService.class, CurrentTenant.class,
                    com.tienda.pos.auth.RootController.class,
                    com.tienda.pos.security.SessionRevocationService.class,
                    com.tienda.pos.security.SessionLogoutService.class,
                    LocalPublicTenantResolver.class, StoreContextService.class,
                    com.tienda.pos.platform.TenantProvisioningService.class,
                    com.tienda.pos.platform.PlatformTenantService.class,
                    com.tienda.pos.platform.PlatformTenantController.class,
                    ProductRepository.class, TenantRepository.class, JpaRepository.class,
                    DataSource.class, EntityManager.class, EntityManagerFactory.class,
                    JdbcTemplate.class, PlatformTransactionManager.class, Flyway.class}) {
                assertThat(context.getBeansOfType(type)).as("No setup bean of type %s", type.getSimpleName()).isEmpty();
            }
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                for (String path : new String[]{"/setup", "/"}) {
                    HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + port + path))
                                    .timeout(Duration.ofSeconds(10)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
                    assertThat(response.body()).contains("Configurar Tienda");
                }
            }
            System.out.println("SETUP_MODE_STARTUP_OK");
        }
    }
}
