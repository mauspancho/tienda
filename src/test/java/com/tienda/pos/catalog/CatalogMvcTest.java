package com.tienda.pos.catalog;

import com.tienda.pos.category.Category;
import com.tienda.pos.hardening.HardeningTestConfiguration;
import com.tienda.pos.product.Product;
import com.tienda.pos.settings.BusinessSettings;
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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(HardeningTestConfiguration.class)
@TestPropertySource("/hardening-test.properties")
@Transactional
class CatalogMvcTest {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    private MockMvc mvc;
    private Tenant publicTenant;
    private Tenant otherTenant;
    private Product publicProduct;
    private Product otherProduct;
    private BusinessSettings settings;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // Insert the foreign tenant first: the public catalog must honor configuration.
        otherTenant = tenant("catalog-other", "Negocio ajeno");
        publicTenant = tenant("catalog-public", "Tienda publica");
        settings = new BusinessSettings();
        settings.setTenant(publicTenant);
        settings.setStoreName("Tienda publica");
        entityManager.persist(settings);
        publicProduct = product(publicTenant, "Arroz publico", "Abarrotes publicos");
        otherProduct = product(otherTenant, "Producto ajeno", "Categoria ajena");
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void anonymousHomeRendersOnlyConfiguredTenantProductsCategoriesAndPromotions() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/index"))
                .andExpect(content().string(containsString("Tienda publica")))
                .andExpect(content().string(containsString("Arroz publico")))
                .andExpect(content().string(containsString("Abarrotes publicos")))
                .andExpect(content().string(not(containsString("Producto ajeno"))))
                .andExpect(content().string(not(containsString("Categoria ajena"))));
    }

    @Test
    void anonymousProductDetailRendersWithoutLogin() throws Exception {
        mvc.perform(get("/producto/{id}", publicProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/product"))
                .andExpect(content().string(containsString("Arroz publico")))
                .andExpect(content().string(containsString("$103.00")));
    }

    @Test
    void foreignOrMissingProductReturns404Not500() throws Exception {
        mvc.perform(get("/producto/{id}", otherProduct.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Producto ajeno"))));
        mvc.perform(get("/producto/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveProductIsNotPublished() throws Exception {
        entityManager.find(Product.class, publicProduct.getId()).setActive(false);
        entityManager.flush();
        mvc.perform(get("/producto/{id}", publicProduct.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Arroz publico"))));
    }

    @Test
    void disabledCatalogDoesNotExposeProductDetails() throws Exception {
        entityManager.find(BusinessSettings.class, settings.getId()).setCatalogEnabled(false);
        entityManager.flush();
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Arroz publico"))));
        mvc.perform(get("/producto/{id}", publicProduct.getId())).andExpect(status().isNotFound());
    }

    @Test
    void sessionAndVisitorSuppliedTenantCannotSwitchPublicCatalog() throws Exception {
        mvc.perform(get("/").with(user("foreign-operator").roles("ADMIN"))
                        .param("tenantId", otherTenant.getId().toString())
                        .header("X-Tenant-Id", otherTenant.getId())
                        .header("Host", "other.example"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Arroz publico")))
                .andExpect(content().string(not(containsString("Producto ajeno"))));
        mvc.perform(get("/producto/{id}", otherProduct.getId())
                        .with(user("foreign-operator").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void inactiveConfiguredTenantDoesNotFallBackToAnotherActiveTenant() throws Exception {
        entityManager.find(Tenant.class, publicTenant.getId()).setActive(false);
        entityManager.flush();
        mvc.perform(get("/")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("Producto ajeno"))));
    }

    @Test
    void administrativeRoutesStillRequireAuthentication() throws Exception {
        mvc.perform(get("/admin/products")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    private Tenant tenant(String code, String name) {
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(name);
        entityManager.persist(tenant);
        return tenant;
    }

    private Product product(Tenant tenant, String name, String categoryName) {
        Category category = new Category();
        category.setTenant(tenant);
        category.setName(categoryName);
        entityManager.persist(category);
        Product product = new Product();
        product.setTenant(tenant);
        product.setCode("SAME-CODE");
        product.setBarcode("1234567890123");
        product.setName(name);
        product.setCategory(category);
        product.setSalePrice(new BigDecimal("103.00"));
        product.setPurchaseCost(new BigDecimal("60.00"));
        product.setCurrentStock(BigDecimal.TEN);
        product.setPromoted(true);
        entityManager.persist(product);
        return product;
    }
}
