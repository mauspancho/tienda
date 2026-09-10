package com.tienda.pos.platform;

import com.tienda.pos.cash.CashService;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.*;
import com.tienda.pos.role.Role;
import com.tienda.pos.sale.*;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(PlatformTestConfiguration.class)
@TestPropertySource(locations = "/hardening-test.properties", properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "tienda.catalog.tenant-code="
})
class PlatformIntegrationTest {
    private static final String PASSWORD = "Test-only-493!";
    @Autowired WebApplicationContext context;
    @Autowired TenantProvisioningService provisioning;
    @Autowired PlatformTenantService tenants;
    @Autowired UserService users;
    @Autowired ProductService products;
    @Autowired CashService cash;
    @Autowired SaleService sales;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @SpyBean PasswordEncoder encoder;
    @SpyBean com.tienda.pos.tenant.PublicTenantResolver publicTenants;
    @SpyBean ProductRepository productRepository;
    @Autowired org.springframework.security.core.session.SessionRegistry sessions;
    MockMvc mvc;
    String suffix, operator, cashier;
    Long operatorId, operatorTenant;
    Shop a, b;

    record Shop(Long id, String username, Long userId, Long productId, String productName, String folio, Long customerId, Long movementId) {}

    @BeforeEach
    void fixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        operator = "platform-" + suffix;
        String hash = encoder.encode(PASSWORD);
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            for (String name : List.of("ROLE_ADMIN", "ROLE_CAJERO", "ROLE_PLATFORM_ADMIN")) {
                if (em.createQuery("select count(r) from Role r where r.name=:name", Long.class).setParameter("name", name).getSingleResult() == 0) {
                    Role role = new Role(); role.setName(name); em.persist(role);
                }
            }
            Tenant tenant = new Tenant(); tenant.setCode(operator); tenant.setName(operator); em.persist(tenant);
            operatorTenant = tenant.getId();
            AppUser user = new AppUser(); user.setTenant(tenant); user.setUsername(operator); user.setFirstName("Global");
            user.setLastName("Operator"); user.setPasswordHash(hash);
            user.getRoles().addAll(em.createQuery("select r from Role r where r.name in ('ROLE_ADMIN','ROLE_PLATFORM_ADMIN')", Role.class).getResultList());
            em.persist(user); operatorId = user.getId();
        });
        a = shop("a"); b = shop("b");
        cashier = "cashier-" + suffix;
        authenticate(a.username(), "ROLE_ADMIN");
        UserForm form = new UserForm(); form.setUsername(cashier); form.setFirstName("Cashier");
        form.setLastName("Only A"); form.setPassword(PASSWORD); form.setActive(true); form.setCashier(true);
        users.create(form);
        SecurityContextHolder.clearContext();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(encoder); }

    private Shop shop(String code) {
        authenticate(operator, "ROLE_PLATFORM_ADMIN", "ROLE_ADMIN");
        Long id = provisioning.create(form(code + "-" + suffix));
        String username = code + "-" + suffix;
        authenticate(username, "ROLE_ADMIN");
        ProductForm product = new ProductForm(); product.setName("Product-" + username); product.setCode("SAME-CODE");
        product.setBarcode("1234567890123"); product.setPurchaseCost(new BigDecimal("60"));
        product.setSalePrice(new BigDecimal("103")); product.setCurrentStock(BigDecimal.TEN);
        Long productId = products.save(product).getId();
        cash.open(username, BigDecimal.ZERO);
        SaleRequest request = new SaleRequest(); request.setPaymentMethod(PaymentMethod.CASH);
        request.setReceivedAmount(new BigDecimal("200"));
        SaleRequest.SaleLineRequest line = new SaleRequest.SaleLineRequest(); line.setProductId(productId); line.setQuantity(BigDecimal.ONE);
        request.setItems(List.of(line));
        String folio = sales.checkout(request).folio();
        return new Shop(id, username, number("select id from app_user where tenant_id=?", id), productId, product.getName(), folio,
                number("select id from customer where tenant_id=?", id),
                number("select min(id) from inventory_movement where tenant_id=?", id));
    }

    static TenantCreateForm form(String code) {
        TenantCreateForm form = new TenantCreateForm(); form.setCode(code); form.setName("Tienda " + code);
        form.setBusinessName("Negocio " + code); form.setPhone("5551234567"); form.setAddress("Calle principal 12");
        form.setAdminUsername(code); form.setAdminFirstName("Admin"); form.setAdminLastName(code);
        form.setPassword(PASSWORD); form.setConfirmPassword(PASSWORD);
        return form;
    }

    @Test void routesEnforcePlatformRoleBeforeProcessingIds() throws Exception {
        mvc.perform(get("/platform/tenants")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin/login"));
        for (String username : List.of(a.username(), cashier)) {
            MockHttpSession session = login(username, "/admin" + (username.equals(cashier) ? "/pos" : ""));
            for (String path : List.of("/platform", "/platform/tenants", "/platform/tenants/" + b.id(), "/platform/tenants/new"))
                mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
            mvc.perform(createRequest("blocked-" + suffix).session(session)).andExpect(status().isForbidden());
            mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(session).with(csrf())).andExpect(status().isForbidden());
        }
        MockHttpSession platform = login(operator, "/platform/tenants");
        mvc.perform(get("/platform/tenants").param("q", suffix).session(platform)).andExpect(status().isOk())
                .andExpect(content().string(containsString(a.username()))).andExpect(content().string(containsString(b.username())));
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform)).andExpect(status().isForbidden());
        authenticate(a.username(), "ROLE_ADMIN");
        assertThatThrownBy(() -> provisioning.create(form("denied-" + suffix))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tenants.detail(b.id())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void provisionsFromMvcWithCompleteDefaultsAndNoCopiedPrivateData() throws Exception {
        MockHttpSession session = login(operator, "/platform/tenants");
        String code = "created-" + suffix;
        mvc.perform(get("/platform/tenants/new").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("Nueva tienda")));
        mvc.perform(createRequest(code).session(session)).andExpect(status().is3xxRedirection());
        Long id = number("select id from tenant where code=?", code);
        for (String table : List.of("business", "business_settings", "branch", "warehouse", "cash_register", "app_user", "customer"))
            assertThat(number("select count(*) from " + table + " where tenant_id=?", id)).as(table).isEqualTo(1);
        assertThat(number("select count(*) from category where tenant_id=?", id)).isEqualTo(9);
        assertThat(number("select count(*) from expense_category where tenant_id=?", id)).isEqualTo(6);
        assertThat(jdbc.queryForList("select name from category where tenant_id=?", String.class, id))
                .containsExactlyInAnyOrder("Refrescos","Botanas","Pan","L\u00e1cteos","Abarrotes","Limpieza","Higiene","Dulces","Otros");
        assertThat(jdbc.queryForList("select name from expense_category where tenant_id=?", String.class, id))
                .containsExactlyInAnyOrder("Servicios","Renta","Insumos","Transporte","Mantenimiento","Otros");
        assertThat(jdbc.queryForObject("select name from customer where tenant_id=?", String.class, id)).isEqualTo("P\u00fablico General");
        for (String table : List.of("product", "sale", "inventory_stock", "supplier", "purchase"))
            assertThat(number("select count(*) from " + table + " where tenant_id=?", id)).as(table).isZero();
        assertThat(jdbc.queryForList("select r.name from role r join user_roles ur on r.id=ur.role_id join app_user u on u.id=ur.user_id where u.tenant_id=?", String.class, id)).containsExactly("ROLE_ADMIN");
        assertThat(number("select count(*) from branch b join business x on b.business_id=x.id where b.tenant_id=? and x.tenant_id=?", id, id)).isEqualTo(1);
        assertThat(number("select count(*) from warehouse w join branch b on w.branch_id=b.id where w.tenant_id=? and b.tenant_id=? and w.code='PRINCIPAL' and b.code='MATRIZ'", id, id)).isEqualTo(1);
        assertThat(number("select count(*) from cash_register c join branch b on c.branch_id=b.id where c.tenant_id=? and b.tenant_id=? and c.code='CAJA01'", id, id)).isEqualTo(1);
        assertThat(number("select count(*) from business_settings s join business b on s.business_id=b.id where s.tenant_id=? and b.tenant_id=?", id, id)).isEqualTo(1);
        login(code, "/admin");
        mvc.perform(get("/platform/tenants/{id}", id).session(session)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("$2a$")))).andExpect(content().string(not(containsString(PASSWORD))));
        mvc.perform(get("/platform/tenants/{id}/edit", id).session(session)).andExpect(status().isOk());
    }

    @Test void invalidAndDuplicateFormsDoNotProvisionOrEchoPasswords() throws Exception {
        MockHttpSession session = login(operator, "/platform/tenants");
        long before = number("select count(*) from tenant");
        mvc.perform(createRequest(a.username()).session(session)).andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(PASSWORD))));
        mvc.perform(createRequest("INVALID code").session(session)).andExpect(status().isBadRequest());
        mvc.perform(createRequest("badpass-" + suffix).with(request -> {
            request.setParameter("confirmPassword", "not-matching"); return request;
        }).session(session)).andExpect(status().isBadRequest());
        assertThat(number("select count(*) from tenant")).isEqualTo(before);
    }

    @Test void lateProvisioningFailureRollsBackEveryInsertedObject() {
        authenticate(operator, "ROLE_PLATFORM_ADMIN");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("tenant","business","business_settings","branch","warehouse","cash_register","app_user","customer","category","expense_category"))
            counts.put(table, number("select count(*) from " + table));
        doThrow(new IllegalStateException("forced administrator failure")).when(encoder).encode(PASSWORD);
        assertThatThrownBy(() -> provisioning.create(form("rollback-" + suffix))).isInstanceOf(IllegalStateException.class);
        counts.forEach((table, count) -> assertThat(number("select count(*) from " + table)).as(table).isEqualTo(count));
    }

    @Test void duplicateUsernameAcrossTenantsRollsBack() {
        authenticate(operator, "ROLE_PLATFORM_ADMIN");
        TenantCreateForm form = form("duplicate-" + suffix); form.setAdminUsername(b.username());
        long before = number("select count(*) from tenant");
        assertThatThrownBy(() -> provisioning.create(form)).isInstanceOf(DomainException.class);
        assertThat(number("select count(*) from tenant")).isEqualTo(before);
    }

    @Test void suspendRejectsNewAndExistingSessionsAndReactivationRestoresAccess() throws Exception {
        MockHttpSession admin = login(b.username(), "/admin");
        MockHttpSession platform = login(operator, "/platform/tenants");
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(formLogin("/admin/login").user(b.username()).password(PASSWORD)).andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?suspended"));
        mvc.perform(get("/admin/products").session(admin)).andExpect(redirectedUrl("/admin/login?expired")).andExpect(unauthenticated());
        assertThat(admin.isInvalid()).isTrue();
        assertThat(number("select count(*) from app_user where tenant_id=? and active=true", b.id())).isEqualTo(1);
        assertThat(number("select count(*) from product where tenant_id=?", b.id())).isEqualTo(1);
        assertThat(number("select count(*) from sale where tenant_id=?", b.id())).isEqualTo(1);
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        login(b.username(), "/admin");
    }

    @Test void platformCanLoginWithSuspendedTenantButNotInactiveAccount() throws Exception {
        jdbc.update("update tenant set active=false where id=?", operatorTenant);
        MockHttpSession session = login(operator, "/platform/tenants");
        mvc.perform(get("/platform/tenants").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/products").session(session)).andExpect(status().isForbidden());
        jdbc.update("update app_user set active=false where id=?", operatorId);
        mvc.perform(formLogin("/admin/login").user(operator).password(PASSWORD)).andExpect(unauthenticated());
        mvc.perform(get("/platform/tenants").session(session)).andExpect(redirectedUrl("/admin/login?expired")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void protectsPlatformUserAtServiceAndMvcLayer() throws Exception {
        jdbc.update("update app_user set tenant_id=? where id=?", a.id(), operatorId);
        MockHttpSession session = login(a.username(), "/admin");
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isOk()).andExpect(content().string(not(containsString(operator))));
        mvc.perform(get("/admin/users/{id}/edit", operatorId).session(session)).andExpect(status().isForbidden());
        for (String action : List.of("toggle","delete"))
            mvc.perform(post("/admin/users/{id}/" + action, operatorId).session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/{id}", operatorId).session(session).with(csrf())
                .param("username",operator).param("firstName","Changed").param("lastName","Changed").param("admin","true")
                .param("active","false").param("password","New-password-123")).andExpect(status().isForbidden());
        authenticate(a.username(), "ROLE_ADMIN");
        UserForm form = new UserForm(); form.setUsername(operator);
        assertThatThrownBy(() -> users.update(operatorId, form)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.toggleActive(operatorId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.deleteOrDeactivate(operatorId)).isInstanceOf(AccessDeniedException.class);
        assertThat(number("select count(*) from app_user u join user_roles ur on ur.user_id=u.id join role r on r.id=ur.role_id where u.id=? and u.active=true and r.name='ROLE_PLATFORM_ADMIN'", operatorId)).isEqualTo(1);
        assertThat(Arrays.stream(UserForm.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)).doesNotContain("platformAdmin", "roles", "tenantId");
    }

    @Test void lifecycleEditsMetadataOnlyAndResetsAdminWithBcrypt() throws Exception {
        MockHttpSession session = login(operator, "/platform/tenants");
        mvc.perform(post("/platform/tenants/{id}", b.id()).session(session).with(csrf()).param("name","Updated B")
                .param("businessName","New business").param("phone","555").param("address","New address")
                .param("currency","MXN").param("currencySymbol","$").param("timezone","America/Mexico_City")
                .param("code","stolen-code").param("tenantId",a.id().toString())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select code from tenant where id=?",String.class,b.id())).isEqualTo(b.username());
        assertThat(number("select tenant_id from app_user where id=?",b.userId())).isEqualTo(b.id());
        String url="/platform/tenants/"+b.id()+"/admins/"+b.userId();
        String originalHash=jdbc.queryForObject("select password_hash from app_user where id=?",String.class,b.userId());
        mvc.perform(post(url+"/reset-password").session(session).with(csrf()).param("password","short").param("confirmPassword","short"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,b.userId())).isEqualTo(originalHash);
        jdbc.update("update app_user set active=false where id=?",b.userId());
        mvc.perform(post(url+"/reactivate").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post(url+"/reset-password").session(session).with(csrf()).param("password","Updated-password-123").param("confirmPassword","Updated-password-123")).andExpect(status().is3xxRedirection());
        String hash=jdbc.queryForObject("select password_hash from app_user where id=?",String.class,b.userId());
        assertThat(hash).startsWith("$2a$"); assertThat(encoder.matches("Updated-password-123",hash)).isTrue();
        mvc.perform(formLogin("/admin/login").user(b.username()).password("Updated-password-123")).andExpect(authenticated());
        mvc.perform(post("/platform/tenants/{id}/admins/{uid}/reactivate",a.id(),b.userId()).session(session).with(csrf())).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(booleans = {true,false})
    void realLoginsIsolateListsAndRejectKnownForeignIds(boolean fromA) throws Exception {
        Shop own=fromA?a:b, other=fromA?b:a;
        MockHttpSession session=login(own.username(),"/admin");
        mvc.perform(get("/admin/api/products/barcode/1234567890123").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.productId())).andExpect(jsonPath("$.name").value(own.productName()));
        mvc.perform(get("/admin/api/products/search").param("q","SAME-CODE").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].id").value(own.productId()));
        for(String path:List.of("/admin/products","/admin/inventory","/admin/sales")) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                    .andExpect(content().string(containsString(own.productName())))
                    .andExpect(content().string(not(containsString(other.productName()))));
        }
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(own.username()))).andExpect(content().string(not(containsString(other.username()))))
                .andExpect(content().string(not(containsString("/platform/tenants"))));
        mvc.perform(get("/admin/products/{id}/edit",other.productId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/products/{id}/barcode-label",other.productId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/users/{id}/edit",other.userId()).session(session)).andExpect(status().isBadRequest());
        for(String action:List.of("toggle","delete"))
            mvc.perform(post("/admin/users/{id}/"+action,other.userId()).session(session).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        mvc.perform(get("/admin/sales/{folio}",other.folio()).session(session)).andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(other.productName()))));
        mvc.perform(get("/admin/inventory").param("productId",other.productId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(other.productName()))));
        mvc.perform(post("/admin/products").session(session).with(csrf()).param("id",other.productId().toString())
                .param("code","ATTACK").param("name","ATTACK").param("salePrice","10")).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("productForm"));
        mvc.perform(post("/admin/inventory/adjust").session(session).with(csrf()).param("productId",other.productId().toString())
                .param("movementType","ADJUSTMENT_IN").param("quantity","1").param("unitCost","2")).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/inventory/movements/{id}/reverse",other.movementId()).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        String payload="{\"customerId\":"+other.customerId()+",\"paymentMethod\":\"CASH\",\"receivedAmount\":200,\"items\":[{\"productId\":"+own.productId()+",\"quantity\":1}]}";
        mvc.perform(post("/admin/pos/checkout").session(session).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select name from product where id=?",String.class,other.productId())).isEqualTo(other.productName());
        assertThat(jdbc.queryForObject("select current_stock from product where id=?",BigDecimal.class,other.productId())).isEqualByComparingTo("9");
        assertThat(number("select count(*) from sale where tenant_id=?",own.id())).isEqualTo(1);
        assertThat(number("select count(*) from app_user where id=? and tenant_id=? and active=true",other.userId(),other.id())).isEqualTo(1);
    }

    @Test void tenantUserFormCannotGrantGlobalRoleAndForeignReferencesAreRejected() throws Exception {
        MockHttpSession session=login(a.username(),"/admin");
        String username="ordinary-"+suffix;
        mvc.perform(post("/admin/users").session(session).with(csrf()).param("username",username)
                .param("firstName","Ordinary").param("lastName","Admin").param("admin","true").param("cashier","false").param("active","true")
                .param("password",PASSWORD).param("platformAdmin","true").param("roles","ROLE_PLATFORM_ADMIN"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        assertThat(jdbc.queryForList("select r.name from role r join user_roles ur on r.id=ur.role_id join app_user u on u.id=ur.user_id where u.username=?",String.class,username))
                .containsExactly("ROLE_ADMIN");
        Long foreignCategory=number("select min(id) from category where tenant_id=?",b.id());
        mvc.perform(post("/admin/products").session(session).with(csrf()).param("code","REJECT-REF").param("name","Rejected reference")
                .param("salePrice","10").param("categoryId",foreignCategory.toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("productForm"));
        assertThat(number("select count(*) from product where tenant_id=?",a.id())).isEqualTo(1);
    }

    @Test void massAssignmentCannotMoveCategoryOrSupplierAndPublicResolverStaysSafe() throws Exception {
        MockHttpSession session=login(a.username(),"/admin");
        Long category=number("select min(id) from category where tenant_id=?",b.id());
        for(String endpoint:List.of("categories","suppliers"))
            mvc.perform(post("/admin/"+endpoint).session(session).with(csrf()).param("id",category.toString())
                    .param("tenant.id",a.id().toString()).param("name","ATTACK")).andExpect(status().isForbidden());
        assertThat(number("select tenant_id from category where id=?",category)).isEqualTo(b.id());
        mvc.perform(get("/")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    @Test void rootRedirectsEveryRoleWithoutResolvingCatalogOrReadingProducts() throws Exception {
        MockHttpSession admin = login(a.username(), "/admin");
        MockHttpSession seller = login(cashier, "/admin/pos");
        MockHttpSession platform = login(operator, "/platform/tenants");
        clearInvocations(publicTenants, productRepository);
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/").session(admin)).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/").session(seller)).andExpect(redirectedUrl("/admin/pos"));
        mvc.perform(get("/").session(platform)).andExpect(redirectedUrl("/platform/tenants"));
        verifyNoInteractions(publicTenants, productRepository);
    }

    @Test void retiredStorefrontIs404ForAnonymousAndAuthenticatedVisitors() throws Exception {
        MockHttpSession admin = login(a.username(), "/admin");
        clearInvocations(publicTenants, productRepository);
        for (String path : List.of("/producto", "/producto/" + a.productId(), "/producto/" + b.productId(),
                "/catalog", "/catalog/products", "/catalog/index")) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(content().string(not(containsString(a.productName()))));
            mvc.perform(get(path).session(admin)).andExpect(status().isNotFound())
                    .andExpect(content().string(not(containsString(b.productName()))));
        }
        verifyNoInteractions(publicTenants, productRepository);
    }

    @Test void invalidCredentialsAndSuspensionHaveDistinctExclusiveMessages() throws Exception {
        mvc.perform(formLogin("/admin/login").user(a.username()).password("wrong"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?error"));
        mvc.perform(formLogin("/admin/login").user("missing-" + suffix).password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?error"));
        authenticate(a.username(), "ROLE_ADMIN");
        users.toggleActive(number("select id from app_user where username=?", cashier));
        SecurityContextHolder.clearContext();
        mvc.perform(formLogin("/admin/login").user(cashier).password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?suspended"));
        Map<String, String> messages = Map.of(
                "error", "Usuario o contrase\u00f1a incorrectos.",
                "suspended", "Tu cuenta o tienda est\u00e1 suspendida. Contacta al administrador.",
                "expired", "Tu sesi\u00f3n fue cerrada porque tu cuenta o tienda fue suspendida.",
                "logout", "Sesi\u00f3n cerrada correctamente.");
        for (var entry : messages.entrySet()) {
            var result = mvc.perform(get("/admin/login").param(entry.getKey(), "")).andExpect(status().isOk());
            result.andExpect(content().string(containsString(entry.getValue())));
            for (var other : messages.entrySet()) if (!other.getKey().equals(entry.getKey()))
                result.andExpect(content().string(not(containsString(other.getValue()))));
        }
        mvc.perform(get("/admin/login?expired&suspended&error&logout")).andExpect(status().isOk())
                .andExpect(content().string(containsString(messages.get("expired"))))
                .andExpect(content().string(not(containsString(messages.get("logout")))))
                .andExpect(content().string(not(containsString(messages.get("error")))))
                .andExpect(content().string(not(containsString(messages.get("suspended")))));
    }

    @ParameterizedTest @ValueSource(strings = {"toggle", "edit", "delete", "delete-with-history"})
    void everyUserSuspensionPathExpiresRegistryAndInvalidatesExistingSession(String action) throws Exception {
        String victim = "victim-" + suffix;
        authenticate(a.username(), "ROLE_ADMIN");
        UserForm form = new UserForm(); form.setUsername(victim); form.setFirstName("Victim");
        form.setLastName("Admin"); form.setPassword(PASSWORD); form.setAdmin(true); form.setActive(true);
        users.create(form);
        Long id = number("select id from app_user where username=?", victim);
        if (action.equals("delete-with-history")) {
            authenticate(victim, "ROLE_ADMIN");
            cash.open(victim, BigDecimal.ZERO);
        }
        MockHttpSession victimSession = login(victim, "/admin");
        mvc.perform(get("/admin").session(victimSession)).andExpect(status().isOk()).andExpect(view().name("dashboard/index"));
        MockHttpSession manager = login(a.username(), "/admin");
        String endpoint = action.equals("edit") ? "" : "/" + (action.startsWith("delete") ? "delete" : "toggle");
        var request = post("/admin/users/" + id + endpoint).session(manager).with(csrf());
        if (action.equals("edit")) request.param("username", victim).param("firstName", "Victim").param("lastName", "Admin")
                .param("admin", "true").param("active", "false").param("password", "");
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        assertExpired(victimSession);
        assertValid(manager);
        assertLoggedOut(victimSession, "/admin");
        if (!action.equals("delete")) {
            assertThat(number("select count(*) from app_user where id=? and active=false", id)).isEqualTo(1);
            mvc.perform(formLogin("/admin/login").user(victim).password(PASSWORD))
                    .andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?suspended"));
        }
    }

    @Test void tenantSuspensionImmediatelyExpiresItsAdminAndCashierButNotOtherTenantsOrPlatform() throws Exception {
        authenticate(b.username(), "ROLE_ADMIN");
        String seller = "seller-b-" + suffix;
        UserForm form = new UserForm(); form.setUsername(seller); form.setFirstName("Seller");
        form.setLastName("B"); form.setPassword(PASSWORD); form.setCashier(true); form.setActive(true); users.create(form);
        MockHttpSession adminA = login(a.username(), "/admin");
        MockHttpSession adminB = login(b.username(), "/admin");
        MockHttpSession cashierB = login(seller, "/admin/pos");
        MockHttpSession platform = login(operator, "/platform/tenants");
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        assertExpired(adminB); assertExpired(cashierB);
        assertValid(adminA); assertValid(platform);
        assertLoggedOut(adminB, "/admin"); assertLoggedOut(cashierB, "/admin/pos");
        mvc.perform(get("/admin").session(adminA)).andExpect(status().isOk());
        mvc.perform(get("/platform/tenants").session(platform)).andExpect(status().isOk());
        assertThat(number("select count(*) from app_user where tenant_id=? and active=true", b.id())).isEqualTo(2);
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(adminB.isInvalid()).isTrue(); assertThat(cashierB.isInvalid()).isTrue();
        login(seller, "/admin/pos");
    }

    @Test void platformSessionSurvivesSuspensionOfItsOwnTenantWhileNormalAdminDoesNot() throws Exception {
        jdbc.update("update app_user set tenant_id=? where id=?", a.id(), operatorId);
        MockHttpSession admin = login(a.username(), "/admin");
        MockHttpSession platform = login(operator, "/platform/tenants");
        mvc.perform(post("/platform/tenants/{id}/toggle", a.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        assertExpired(admin); assertValid(platform);
        assertLoggedOut(admin, "/admin");
        mvc.perform(get("/platform/tenants").session(platform)).andExpect(status().isOk());
        mvc.perform(get("/admin/products").session(platform)).andExpect(status().isForbidden());
        assertValid(platform);
        login(operator, "/platform/tenants");
    }

    @Test void rollbackOfUserOrTenantSuspensionDoesNotExpireSessions() throws Exception {
        MockHttpSession seller = login(cashier, "/admin/pos");
        MockHttpSession admin = login(a.username(), "/admin");
        authenticate(a.username(), "ROLE_ADMIN");
        assertThatThrownBy(() -> new TransactionTemplate(tx).executeWithoutResult(status -> {
            users.toggleActive(number("select id from app_user where username=?", cashier));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        authenticate(operator, "ROLE_PLATFORM_ADMIN");
        assertThatThrownBy(() -> new TransactionTemplate(tx).executeWithoutResult(status -> {
            tenants.toggle(a.id());
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        SecurityContextHolder.clearContext();
        assertValid(seller); assertValid(admin);
        mvc.perform(get("/admin").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/pos").session(seller)).andExpect(status().isOk());
    }

    @Test void reactivationCannotResurrectAnAlreadyExpiredSessionOrDisabledUser() throws Exception {
        MockHttpSession admin = login(b.username(), "/admin");
        MockHttpSession platform = login(operator, "/platform/tenants");
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        assertExpired(admin);
        jdbc.update("update app_user set active=false where id=?", b.userId());
        mvc.perform(post("/platform/tenants/{id}/toggle", b.id()).session(platform).with(csrf())).andExpect(status().is3xxRedirection());
        assertExpired(admin);
        assertThat(number("select count(*) from app_user where id=? and active=false", b.userId())).isEqualTo(1);
        jdbc.update("update app_user set active=true where id=?", b.userId());
        mvc.perform(get("/admin").session(admin)).andExpect(redirectedUrl("/admin/login?sessionExpired")).andExpect(unauthenticated());
        assertThat(admin.isInvalid()).isTrue();
    }

    @Test void deletedAccountBackstopLogsOutAndInsufficientRoleDoesNot() throws Exception {
        MockHttpSession seller = login(cashier, "/admin/pos");
        mvc.perform(get("/platform/tenants").session(seller)).andExpect(status().isForbidden());
        assertValid(seller);
        mvc.perform(get("/admin/pos").session(seller)).andExpect(status().isOk());
        Long id = number("select id from app_user where username=?", cashier);
        jdbc.update("delete from user_roles where user_id=?", id);
        jdbc.update("delete from app_user where id=?", id);
        assertLoggedOut(seller, "/admin/pos");
    }

    @Test void concurrentLoginExpiresPreviousSessionAndExplicitLogoutClearsCookie() throws Exception {
        MockHttpSession first = login(cashier, "/admin/pos");
        MockHttpSession second = login(cashier, "/admin/pos");
        assertExpired(first); assertValid(second);
        mvc.perform(get("/admin/pos").session(first)).andExpect(redirectedUrl("/admin/login?sessionExpired")).andExpect(unauthenticated());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(post("/admin/logout").session(second).with(csrf())).andExpect(redirectedUrl("/admin/login?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0)).andExpect(unauthenticated());
        assertThat(second.isInvalid()).isTrue();
    }

    @Test void rootInvalidatesAuthenticatedSessionWithoutAnOperationalRole() throws Exception {
        MockHttpSession session = login(cashier, "/admin/pos");
        var invalidRole = UsernamePasswordAuthenticationToken.authenticated(cashier, "",
                AuthorityUtils.createAuthorityList("ROLE_UNSUPPORTED"));
        session.setAttribute("SPRING_SECURITY_CONTEXT",
                new org.springframework.security.core.context.SecurityContextImpl(invalidRole));
        clearInvocations(publicTenants, productRepository);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/admin/login")).andExpect(unauthenticated())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(publicTenants, productRepository);
    }

    @Test void loginCannotLeaveAnAuthenticatedSessionWithNoOperationalRole() throws Exception {
        Long id = number("select id from app_user where username=?", cashier);
        jdbc.update("delete from user_roles where user_id=?", id);
        mvc.perform(formLogin("/admin/login").user(cashier).password(PASSWORD))
                .andExpect(redirectedUrl("/admin/login")).andExpect(unauthenticated())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
    }

    @Test void externallyDisabledAccountIsLoggedOutBeforePostAuthorizationOrCsrfChecks() throws Exception {
        MockHttpSession session = login(cashier, "/admin/pos");
        jdbc.update("update app_user set active=false where username=?", cashier);
        mvc.perform(post("/admin/pos/checkout").session(session)).andExpect(redirectedUrl("/admin/login?expired"))
                .andExpect(unauthenticated()).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void removingPlatformPrivilegeStillDeniesAnOlderSessionWithoutTreatingItAsSuspension() throws Exception {
        MockHttpSession session = login(operator, "/platform/tenants");
        jdbc.update("delete from user_roles where user_id=? and role_id=(select id from role where name='ROLE_PLATFORM_ADMIN')", operatorId);
        mvc.perform(get("/platform/tenants").session(session)).andExpect(status().isForbidden());
        assertValid(session);
    }

    private void assertExpired(MockHttpSession session) {
        assertThat(sessions.getSessionInformation(session.getId())).isNotNull();
        assertThat(sessions.getSessionInformation(session.getId()).isExpired()).isTrue();
    }

    private void assertValid(MockHttpSession session) {
        assertThat(session.isInvalid()).isFalse();
        assertThat(sessions.getSessionInformation(session.getId())).isNotNull();
        assertThat(sessions.getSessionInformation(session.getId()).isExpired()).isFalse();
    }

    private void assertLoggedOut(MockHttpSession session, String path) throws Exception {
        String oldId = session.getId();
        mvc.perform(get(path).session(session)).andExpect(redirectedUrl("/admin/login?expired"))
                .andExpect(cookie().maxAge("JSESSIONID", 0)).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        assertThat(sessions.getSessionInformation(oldId)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        mvc.perform(get(path).cookie(new jakarta.servlet.http.Cookie("JSESSIONID", oldId)))
                .andExpect(redirectedUrlPattern("**/admin/login")).andExpect(unauthenticated());
    }

    private MockHttpServletRequestBuilder createRequest(String code) {
        return post("/platform/tenants").with(csrf()).param("name","Tienda "+code).param("code",code)
                .param("businessName","Business "+code).param("phone","5551234567").param("address","Calle 12")
                .param("currency","MXN").param("currencySymbol","$").param("timezone","America/Mexico_City")
                .param("adminFirstName","Admin").param("adminLastName","Test").param("adminUsername",code)
                .param("password",PASSWORD).param("confirmPassword",PASSWORD);
    }
    private MockHttpSession login(String username,String destination) throws Exception {
        SecurityContextHolder.clearContext();
        return (MockHttpSession)mvc.perform(formLogin("/admin/login").user(username).password(PASSWORD))
                .andExpect(authenticated().withUsername(username)).andExpect(redirectedUrl(destination)).andReturn().getRequest().getSession(false);
    }
    private static void authenticate(String username,String... roles) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username,"",AuthorityUtils.createAuthorityList(roles)));
    }
    private long number(String sql,Object...args) { return jdbc.queryForObject(sql,Long.class,args); }
}
