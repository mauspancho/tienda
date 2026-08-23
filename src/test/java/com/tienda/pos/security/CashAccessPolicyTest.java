package com.tienda.pos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CashAccessPolicyTest {

    private static final Path ROOT = Path.of("");

    @Test
    void cashManagementScreenIsAdminOnly() throws Exception {
        String securityConfig = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/security/SecurityConfig.java"));
        String cashController = Files.readString(ROOT.resolve("src/main/java/com/tienda/pos/cash/CashController.java"));

        assertThat(securityConfig)
                .contains("/cash/**")
                .contains("hasRole(\"ADMIN\")");
        assertThat(cashController)
                .contains("@PreAuthorize(\"hasRole('ADMIN')\")")
                .doesNotContain("hasAnyRole('ADMIN','CAJERO')");
    }

    @Test
    void cashMenuLinkIsVisibleOnlyToAdmins() throws Exception {
        String layout = Files.readString(ROOT.resolve("src/main/resources/templates/fragments/layout.html"));

        assertThat(layout)
                .contains("<li sec:authorize=\"hasRole('ADMIN')\"><a th:href=\"@{/cash}\">Caja</a></li>")
                .doesNotContain("<li><a th:href=\"@{/cash}\">Caja</a></li>");
    }
}