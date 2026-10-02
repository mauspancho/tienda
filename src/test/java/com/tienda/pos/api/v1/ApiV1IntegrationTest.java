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

    private Product product;

    @BeforeAll
    void ensureNormalModeForSuite() {
        assertThat(System.getProperty("tienda.setup-mode")).isEqualTo("false");
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
