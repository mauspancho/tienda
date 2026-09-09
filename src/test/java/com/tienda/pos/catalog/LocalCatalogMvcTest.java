package com.tienda.pos.catalog;

import com.tienda.pos.hardening.HardeningTestConfiguration;
import com.tienda.pos.product.Product;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(HardeningTestConfiguration.class)
@TestPropertySource(locations = "/hardening-test.properties", properties = {
        "tienda.catalog.tenant-code=",
        "spring.datasource.url=jdbc:h2:mem:local-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@Transactional
class LocalCatalogMvcTest {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void singleTenantInstallationPublishesHomeAndProductWithoutLoginOrExtraConfiguration() throws Exception {
        Product product = new Product();
        product.setTenant(tenant("local"));
        product.setCode("RICE");
        product.setName("Arroz local");
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Arroz local")));
        mvc.perform(get("/producto/{id}", product.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Arroz local")));
    }

    @Test
    void multipleActiveTenantsRequireExplicitServerConfiguration() throws Exception {
        tenant("first");
        tenant("second");
        entityManager.flush();
        mvc.perform(get("/")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("<h1>503</h1>")));
    }

    @Test
    void noActiveTenantIsUnavailableRatherThanRedirectingToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isServiceUnavailable());
    }

    private Tenant tenant(String code) {
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(code);
        entityManager.persist(tenant);
        return tenant;
    }
}
