package com.tienda.pos.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.category.Category;
import com.tienda.pos.category.CategoryRepository;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.product.UnitType;
import com.tienda.pos.role.Role;
import com.tienda.pos.role.RoleRepository;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.credit.CreditPaymentRepository;
import com.tienda.pos.customer.CustomerRepository;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiV1IntegrationTest {

    private static final Path PRODUCT_IMAGE_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"), "tienda-api-v1-product-images");

    static {
        System.setProperty("tienda.setup-mode", "false");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:api-v1;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("tienda.api.jwt-secret", () -> "test-secret-with-at-least-thirty-two-bytes");
        registry.add("tienda.product-images.directory", () -> PRODUCT_IMAGE_DIRECTORY.toString());
        registry.add("springdoc.api-docs.enabled", () -> "false");
        registry.add("springdoc.swagger-ui.enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RoleRepository roleRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired SaleRepository saleRepository;
    @Autowired CreditPaymentRepository creditPaymentRepository;
    @Autowired CustomerRepository customerRepository;

    private Product product;

    @BeforeAll
    void ensureNormalModeForSuite() throws Exception {
        assertThat(System.getProperty("tienda.setup-mode")).isEqualTo("false");
        Files.createDirectories(PRODUCT_IMAGE_DIRECTORY);
        Files.write(PRODUCT_IMAGE_DIRECTORY.resolve("public-test.jpg"), new byte[]{1, 2, 3, 4});
    }

    @Test
    void productImagesArePublicAndAllowCrossOriginReads() throws Exception {
        mockMvc.perform(get("/uploads/products/public-test.jpg")
                        .header("Origin", "https://automation.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.queryForList("select table_name from information_schema.tables where table_schema = 'public'", String.class)
                .forEach(table -> jdbcTemplate.execute("truncate table " + table));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"));
        Role cashierRole = roleRepository.save(new Role("ROLE_CAJERO"));
        userRepository.save(seedUser("admin", "Administrador", adminRole));
        userRepository.save(seedUser("cajero", "Caja", cashierRole));

        Category category = new Category();
        category.setName("Bebidas");
        category = categoryRepository.save(category);

        product = new Product();
        product.setCode("COCA-600");
        product.setBarcode("750000000001");
        product.setName("Coca Cola 600 ml");
        product.setBrand("Coca-Cola");
        product.setPresentation("600 ml");
        product.setCategory(category);
        product.setPurchaseCost(new BigDecimal("10.00"));
        product.setSalePrice(new BigDecimal("18.50"));
        product.setCurrentStock(new BigDecimal("10.000"));
        product.setMinimumStock(BigDecimal.ONE);
        product.setUnit(UnitType.PIEZA);
        product = productRepository.save(product);
    }

    @Test
    void healthIsPublicAndIdentifiesCompatibleServer() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("Tienda POS"))
                .andExpect(jsonPath("$.apiVersion").value("v1"));
    }

    @Test
    void loginSuccessAndMeReturnUserWithoutPasswordData() throws Exception {
        JsonNode auth = login("admin", "password123");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void incorrectLoginReturnsJson401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointRequiresValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void adminCanAccessAdminApiAndCashierIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(login("admin", "password123"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(login("cajero", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void cashierCanUsePosAndReadProductsButCannotModifyThem() throws Exception {
        String token = bearer(login("cajero", "password123"));
        mockMvc.perform(get("/api/v1/pos").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashOpen").value(false));
        mockMvc.perform(get("/api/v1/products/search").param("q", "coca").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Coca Cola 600 ml"));
        mockMvc.perform(post("/api/v1/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void barcodeLookupReturnsMatchingProduct() throws Exception {
        mockMvc.perform(get("/api/v1/products/barcode/750000000001")
                        .header("Authorization", bearer(login("cajero", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COCA-600"));
    }

    @Test
    void refreshRotatesAndLogoutRevokesRefreshToken() throws Exception {
        JsonNode login = login("admin", "password123");
        String first = login.get("refreshToken").asText();
        JsonNode refreshed = refresh(first, 200);
        refresh(first, 401);
        String second = refreshed.get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(second))))
                .andExpect(status().isNoContent());
        refresh(second, 401);
    }

    @Test
    void saleUsesExistingServiceAndIdempotencyPreventsDuplicateCheckout() throws Exception {
        String token = bearer(login("cajero", "password123"));
        mockMvc.perform(post("/api/v1/cash/open").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openingAmount\":100}"))
                .andExpect(status().isCreated());
        String request = "{\"discount\":0,\"paymentMethod\":\"CASH\",\"receivedAmount\":50," +
                "\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}";
        mockMvc.perform(post("/api/v1/sales").header("Authorization", token)
                        .header("Idempotency-Key", "sale-mobile-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(37.00));
        mockMvc.perform(post("/api/v1/sales").header("Authorization", token)
                        .header("Idempotency-Key", "sale-mobile-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());

        assertThat(saleRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock())
                .isEqualByComparingTo("8.000");
    }

    @Test
    void creditLifecycleKeepsInventoryCashBalancesIdempotencyAndPermissionsConsistent() throws Exception {
        product.setSalePrice(new BigDecimal("100.00"));
        productRepository.save(product);
        String admin = bearer(login("admin", "password123"));
        String cashier = bearer(login("cajero", "password123"));
        String customerJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"lastName\":\"López\",\"active\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fullName").value("Ana López"))
                .andReturn().getResponse().getContentAsString();
        long customerId = objectMapper.readTree(customerJson).get("id").asLong();
        mockMvc.perform(post("/api/v1/cash/open").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openingAmount\":100}"))
                .andExpect(status().isCreated());

        String firstCredit = "{\"customerId\":" + customerId + ",\"settlementType\":\"CREDIT\",\"discount\":0," +
                "\"paymentMethod\":\"CASH\",\"receivedAmount\":0,\"items\":[{\"productId\":" + product.getId() + ",\"quantity\":2}]}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/sales").header("Authorization", cashier)
                            .header("Idempotency-Key", "credit-200").contentType(MediaType.APPLICATION_JSON).content(firstCredit))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.settlementType").value("CREDIT"))
                    .andExpect(jsonPath("$.pendingBalance").value(200.00)).andExpect(jsonPath("$.payment").doesNotExist());
        }
        assertThat(saleRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("8.000");
        assertThat(jdbcTemplate.queryForObject("select count(*) from cash_movement where type='SALE'", Long.class)).isZero();

        String payment50 = "{\"amount\":50,\"method\":\"TRANSFER\",\"notes\":\"Primer abono\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/credits/customers/{id}/payments", customerId)
                            .header("Authorization", cashier).header("Idempotency-Key", "payment-50")
                            .contentType(MediaType.APPLICATION_JSON).content(payment50))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.customerBalance").value(150.00));
        }
        assertThat(creditPaymentRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("8.000");

        String secondCredit = firstCredit.replace("\"quantity\":2", "\"quantity\":1");
        mockMvc.perform(post("/api/v1/pos/checkout").header("Authorization", cashier)
                        .header("Idempotency-Key", "credit-100").contentType(MediaType.APPLICATION_JSON).content(secondCredit))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/credits/customers/{id}/payments", customerId)
                        .header("Authorization", cashier).header("Idempotency-Key", "payment-250")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":250,\"method\":\"CASH\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerBalance").value(0.00));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("7.000");
        assertThat(jdbcTemplate.queryForObject("select count(*) from cash_movement where type='CREDIT_PAYMENT'", Long.class)).isEqualTo(1);

        long cashPaymentId = creditPaymentRepository.findAll().stream()
                .filter(p -> p.getAmount().compareTo(new BigDecimal("250.00")) == 0).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/api/v1/credits/payments/{id}/reverse", cashPaymentId).header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Corrección\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/credits/payments/{id}/reverse", cashPaymentId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Corrección administrativa\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from cash_movement where type='CREDIT_PAYMENT_REVERSAL'", Long.class)).isEqualTo(1);

        long secondSaleId = saleRepository.findByCustomerIdAndSettlementTypeOrderBySaleDateAsc(customerId,
                com.tienda.pos.sale.SettlementType.CREDIT).get(1).getId();
        mockMvc.perform(post("/api/v1/credits/sales/{id}/cancel", secondSaleId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Producto entregado por error\"}"))
                .andExpect(status().isNoContent());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("8.000");
        mockMvc.perform(get("/api/v1/credits/customers/{id}", customerId).header("Authorization", cashier))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer.balance").value(150.00));

        String chargeRequest = "{\"amount\":75,\"concept\":\"Préstamo adicional\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/credits/customers/{id}/charges", customerId)
                            .header("Authorization", cashier).header("Idempotency-Key", "charge-75")
                            .contentType(MediaType.APPLICATION_JSON).content(chargeRequest))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.customerBalance").value(225.00));
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from credit_charge", Long.class)).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("8.000");
        mockMvc.perform(post("/api/v1/credits/customers/{id}/payments", customerId)
                        .header("Authorization", cashier).header("Idempotency-Key", "payment-charge-75")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":75,\"method\":\"TRANSFER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerBalance").value(150.00));
        long chargeId = jdbcTemplate.queryForObject("select id from credit_charge", Long.class);
        mockMvc.perform(post("/api/v1/credits/charges/{id}/reverse", chargeId).header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sin permiso\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/credits/charges/{id}/reverse", chargeId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cargo equivocado\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/credits/customers/{id}", customerId).header("Authorization", cashier))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer.balance").value(75.00));
        long chargePaymentId = creditPaymentRepository.findAll().stream()
                .filter(p -> p.getAmount().compareTo(new BigDecimal("75.00")) == 0).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/api/v1/credits/payments/{id}/reverse", chargePaymentId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Corrección del cargo\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/credits/customers/{id}", customerId).header("Authorization", cashier))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer.balance").value(150.00));
        mockMvc.perform(get("/admin/credits").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/credits/customers/{id}", customerId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/credits/new").param("customerId", String.valueOf(customerId))
                        .with(user("cajero").roles("CAJERO")))
                .andExpect(status().isOk());
    }

    @Test
    void validationErrorsUseCommonJsonContract() throws Exception {
        String token = bearer(login("cajero", "password123"));
        mockMvc.perform(post("/api/v1/sales").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discount\":0,\"paymentMethod\":\"CASH\",\"receivedAmount\":0,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    void webLoginSessionAndExistingInternalProductApiStillWork() throws Exception {
        mockMvc.perform(get("/admin/login")).andExpect(status().isOk());
        mockMvc.perform(formLogin("/admin/login").user("admin").password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
        mockMvc.perform(get("/admin/products")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/login")));
        mockMvc.perform(get("/admin/api/products/search").param("q", "coca")
                        .with(user("cajero").roles("CAJERO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Coca Cola 600 ml"));
    }

    private AppUser seedUser(String username, String firstName, Role role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFirstName(firstName);
        user.setLastName("Pruebas");
        user.getRoles().add(role);
        return user;
    }

    private JsonNode login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody(username, password));
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refresh(String token, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenBody(token))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return response.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response);
    }

    private String bearer(JsonNode auth) {
        return "Bearer " + auth.get("accessToken").asText();
    }

    private record LoginBody(String username, String password) {
    }

    private record TokenBody(String refreshToken) {
    }
}
