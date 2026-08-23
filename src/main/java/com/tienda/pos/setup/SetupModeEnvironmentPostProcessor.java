package com.tienda.pos.setup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class SetupModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String EXCLUDES = String.join(",",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean configured = hasExternalDatasourceConfig();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("tienda.setup-mode", configured ? "false" : "true");
        if (!configured) {
            properties.put("spring.autoconfigure.exclude", EXCLUDES);
            properties.put("spring.flyway.enabled", "false");
            properties.put("spring.jpa.hibernate.ddl-auto", "none");
        }
        environment.getPropertySources().addFirst(new MapPropertySource("tiendaSetupMode", properties));
    }

    private boolean hasExternalDatasourceConfig() {
        Path yml = Path.of("config", "application.yml");
        Path yaml = Path.of("config", "application.yaml");
        Path properties = Path.of("config", "application.properties");
        return containsDatasource(yml) || containsDatasource(yaml) || containsDatasource(properties);
    }

    private boolean containsDatasource(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            String content = Files.readString(path);
            return content.contains("spring:") && content.contains("datasource:")
                    || content.contains("spring.datasource.url");
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
