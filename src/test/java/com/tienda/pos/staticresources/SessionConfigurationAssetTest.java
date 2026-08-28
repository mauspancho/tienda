package com.tienda.pos.staticresources;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConfigurationAssetTest {

    private static final Path ROOT = Path.of("");

    @Test
    void sessionCookieLastsForWorkday() throws Exception {
        String application = Files.readString(ROOT.resolve("src/main/resources/application.yml"));

        assertThat(application)
                .contains("timeout: 12h")
                .contains("max-age: 12h")
                .contains("http-only: true")
                .contains("same-site: lax");
    }
}