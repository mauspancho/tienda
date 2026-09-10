package com.tienda.pos.setup;

import com.tienda.pos.catalog.CatalogService;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.hardening.HardeningTestConfiguration;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.LocalPublicTenantResolver;
import com.tienda.pos.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig(HardeningTestConfiguration.class)
@TestPropertySource("/hardening-test.properties")
class NormalModeStartupTest {

    @Autowired private ApplicationContext context;

    @Test
    void normalModeCreatesRepositoriesAndDependentServices() throws Exception {
        assertThat(context.getEnvironment().getProperty("tienda.setup-mode")).isEqualTo("false");
        for (Class<?> type : new Class<?>[]{ProductRepository.class, TenantRepository.class,
                com.tienda.pos.auth.RootController.class, com.tienda.pos.security.SessionRevocationService.class,
                org.springframework.security.core.session.SessionRegistry.class,
                CatalogService.class, CurrentTenant.class, LocalPublicTenantResolver.class,
                StoreContextService.class}) {
            assertThat(context.getBean(type)).isNotNull();
        }
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
    }
}
