package com.tienda.pos.staticresources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserInterfaceAssetTest {

    private static final Path ROOT = Path.of("");

    @Test
    void userRoleCheckboxesHaveVisibleStateIndicators() throws Exception {
        String users = Files.readString(ROOT.resolve("src/main/resources/templates/users/index.html"));
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        assertThat(users)
                .contains("user-access-box")
                .contains("user-access-label")
                .contains("th:field=\"*{admin}\"")
                .contains("th:field=\"*{cashier}\"")
                .contains("th:field=\"*{active}\"");
        assertThat(css)
                .contains(".user-access-input:checked + .user-access-box")
                .contains("content: \"✓\"")
                .contains("content: \"×\"")
                .contains(".user-access-option:has(.user-access-input:checked)");
    }
}