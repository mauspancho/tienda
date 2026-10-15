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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        "tienda.catalog.tenant-code=",
        "tienda.product-images.directory=target/backup-test-products",
        "tienda.catalog-images.directory=target/backup-test-catalog"
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
    @Autowired com.tienda.pos.credit.CreditService credits;
    @Autowired com.tienda.pos.customer.CustomerService customers;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.tienda.pos.backup.TenantBackupService backups;
    @Autowired com.tienda.pos.whatsapp.automation.PlatformAutomationTokenService automationTokens;
    @SpyBean PasswordEncoder encoder;
    @SpyBean com.tienda.pos.tenant.PublicTenantResolver publicTenants;
    @SpyBean ProductRepository productRepository;
    @Autowired org.springframework.security.core.session.SessionRegistry sessions;
    MockMvc mvc;
    String suffix, operator, cashier;
    Long operatorId, operatorTenant;
    Shop a, b;

    record Shop(Long id, String username, Long userId, Long productId, String productName, String folio, Long customerId, Long movementId) {}
    record AutomationFixture(String rawToken, Long tokenId) {}
    record AutomationRequest(String path, String expected, String excluded) {}

    private byte[] exportBackup(long tenant) throws Exception {
        authenticate(operator, "ROLE_PLATFORM_ADMIN", "ROLE_ADMIN");
        var out = new java.io.ByteArrayOutputStream(); backups.exportArchive(tenant, out); return out.toByteArray();
    }

    private Map<String, byte[]> zipEntries(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) entries.put(entry.getName(), in.readAllBytes());
        }
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private org.springframework.mock.web.MockMultipartFile archive(byte[] zip) {
        return new org.springframework.mock.web.MockMultipartFile("backupFile", "backup.zip", "application/zip", zip);
    }

    @Test void tenantBackupRoundTripRemapsIdsAndLeavesOtherTenantUntouched() throws Exception {
        byte[] beforeB = exportBackup(b.id());
        byte[] backup = exportBackup(a.id());
        var entries = zipEntries(backup);
        String exported = new String(entries.get("data/app_user.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(exported).contains(a.username()).doesNotContain(b.username()).doesNotContain("$2a$");
        assertThat(entries.keySet()).doesNotContain("data/role.json", "data/api_refresh_token.json", "data/tenant.json");
        String hash = jdbc.queryForObject("select password_hash from app_user where id=?", String.class, a.userId());
        tenants.toggle(a.id());
        jdbc.update("update product set name='Changed' where id=?", a.productId());
        backups.importArchive(a.id(), archive(backup));
        assertThat(jdbc.queryForObject("select name from product where tenant_id=?", String.class, a.id())).isEqualTo(a.productName());
        long newProduct = number("select id from product where tenant_id=?", a.id());
        assertThat(newProduct).isNotEqualTo(a.productId());
        assertThat(number("select product_id from sale_item where sale_id=(select id from sale where tenant_id=?)", a.id())).isEqualTo(newProduct);
        assertThat(jdbc.queryForObject("select quantity from inventory_stock where tenant_id=?", BigDecimal.class, a.id())).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?", String.class, a.userId())).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select active from tenant where id=?", Boolean.class, a.id())).isFalse();
        var afterB = zipEntries(exportBackup(b.id()));
        for (var entry : zipEntries(beforeB).entrySet()) if (!entry.getKey().equals("manifest.json"))
            assertThat(afterB.get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
    }

    @Test void backupRoutesArePlatformOnlyAndRequireCsrfAndTenantConfirmation() throws Exception {
        String path = "/platform/tenants/" + a.id() + "/backup";
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        for (String username : List.of(a.username(), cashier)) {
            var session = login(username, username.equals(cashier) ? "/admin/pos" : "/admin");
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
            mvc.perform(get(path + "/export").session(session)).andExpect(status().isForbidden());
            mvc.perform(multipart(path + "/import").file(archive(new byte[]{1})).session(session).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        var platform = login(operator, "/platform/tenants");
        mvc.perform(get(path).session(platform)).andExpect(status().isOk()).andExpect(content().string(containsString(a.username())));
        mvc.perform(get(path + "/export").session(platform)).andExpect(status().isOk()).andExpect(content().contentType("application/zip"));
        mvc.perform(multipart(path + "/import").file(archive(new byte[]{1})).session(platform)).andExpect(status().isForbidden());
        mvc.perform(multipart(path + "/import").file(archive(new byte[]{1})).param("confirmation", "IMPORTAR " + b.username()).session(platform).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
    }

    @Test void backupRejectsActiveTenantAndForeignReferencesWithFullRollback() throws Exception {
        byte[] backup = exportBackup(a.id());
        assertThatThrownBy(() -> backups.importArchive(a.id(), archive(backup))).isInstanceOf(DomainException.class).hasMessageContaining("Suspende");
        tenants.toggle(a.id());
        var entries = zipEntries(backup);
        var manifest = json.readTree(entries.get("manifest.json"));
        int index = -1;
        for (var definition : manifest.get("tables")) if (definition.get("name").asText().equals("sale_item")) {
            for (int i=0; i<definition.get("columns").size(); i++)
                if (definition.get("columns").get(i).get("name").asText().equals("product_id")) index=i;
        }
        var rows = (com.fasterxml.jackson.databind.node.ArrayNode) json.readTree(entries.get("data/sale_item.json"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) rows.get(0)).set(index, json.getNodeFactory().textNode(b.productId().toString()));
        entries.put("data/sale_item.json", json.writeValueAsBytes(rows));
        byte[] malicious = zip(entries);
        assertThatThrownBy(() -> backups.importArchive(a.id(), archive(malicious))).isInstanceOf(DomainException.class).hasMessageContaining("Referencia fuera");
        assertThat(number("select id from product where tenant_id=?", a.id())).isEqualTo(a.productId());
        assertThat(number("select id from product where tenant_id=?", b.id())).isEqualTo(b.productId());
    }

    @Test void backupRejectsZipTraversalAndIncompleteManifest() throws Exception {
        var entries = zipEntries(exportBackup(a.id())); tenants.toggle(a.id());
        entries.put("../escape.txt", new byte[]{1}); byte[] badPath = zip(entries);
        assertThatThrownBy(() -> backups.importArchive(a.id(), archive(badPath))).isInstanceOf(DomainException.class);
        entries.remove("../escape.txt"); entries.remove("data/product.json"); byte[] incomplete = zip(entries);
        assertThatThrownBy(() -> backups.importArchive(a.id(), archive(incomplete))).isInstanceOf(java.io.IOException.class);
        assertThat(number("select id from product where tenant_id=?", a.id())).isEqualTo(a.productId());
    }

    @Test void backupImportsMainV12FormatIntoSelectedTenantAndBuildsWarehouseStock() throws Exception {
        var entries = zipEntries(exportBackup(a.id()));
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(entries.get("manifest.json"));
        manifest.put("format", "tienda-pos-store-backup"); manifest.put("schemaVersion", 12); manifest.remove("sourceTenant");
        var tables = json.createArrayNode();
        Set<String> structural = Set.of("business", "branch", "warehouse", "cash_register", "inventory_stock");
        Set<String> extraColumns = Set.of("tenant_id", "business_id", "branch_id", "warehouse_id", "cash_register_id", "auth_epoch");
        for (var definition : manifest.get("tables")) {
            String table = definition.get("name").asText();
            if (structural.contains(table)) { entries.remove("data/" + table + ".json"); continue; }
            var cols = json.createArrayNode(); var rows = json.createArrayNode(); List<Integer> kept = new ArrayList<>();
            for (int i=0; i<definition.get("columns").size(); i++) if (!extraColumns.contains(definition.get("columns").get(i).get("name").asText())) {
                cols.add(definition.get("columns").get(i)); kept.add(i);
            }
            for (var oldRow : json.readTree(entries.get("data/" + table + ".json"))) {
                var row = json.createArrayNode(); kept.forEach(i -> row.add(oldRow.get(i))); rows.add(row);
            }
            if (table.equals("app_user")) {
                cols.add(json.createObjectNode().put("name", "password_hash"));
                for (var row : rows) ((com.fasterxml.jackson.databind.node.ArrayNode) row).add("!MUST-NOT-REPLACE-PASSWORD");
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) definition).set("columns", cols);
            tables.add(definition); entries.put("data/" + table + ".json", json.writeValueAsBytes(rows));
        }
        for (String table : List.of("role", "user_roles", "api_refresh_token", "api_idempotency_record")) {
            var def = json.createObjectNode().put("name", table).put("rowCount", 0);
            def.set("columns", json.createArrayNode()); tables.add(def); entries.put("data/" + table + ".json", "[]".getBytes());
        }
        manifest.set("tables", tables); entries.put("manifest.json", json.writeValueAsBytes(manifest));
        String password = jdbc.queryForObject("select password_hash from app_user where id=?", String.class, a.userId());
        tenants.toggle(a.id()); backups.importArchive(a.id(), archive(zip(entries)));
        assertThat(jdbc.queryForObject("select quantity from inventory_stock where tenant_id=?", BigDecimal.class, a.id())).isEqualByComparingTo("9");
        assertThat(number("select count(*) from sale s join warehouse w on w.id=s.warehouse_id where s.tenant_id=? and w.tenant_id=?", a.id(), a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?", String.class, a.userId())).isEqualTo(password);
        assertThat(number("select id from product where tenant_id=?", b.id())).isEqualTo(b.productId());
    }

    @Test void backupOnlyIncludesReferencedImagesAndNeverOverwritesOtherStoreFiles() throws Exception {
        var props = context.getBean(ProductImagesProperties.class);
        var directory = java.nio.file.Path.of(props.getDirectory()).toAbsolutePath(); java.nio.file.Files.createDirectories(directory);
        String nameA = "backup-test-a-" + suffix + ".png", nameB = "backup-test-b-" + suffix + ".png";
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS1kAAAAASUVORK5CYII=");
        java.nio.file.Path restored = null;
        try {
            java.nio.file.Files.write(directory.resolve(nameA), png); java.nio.file.Files.write(directory.resolve(nameB), png);
            jdbc.update("update product set image_url=? where id=?", props.normalizedPublicPath() + "/" + nameA, a.productId());
            jdbc.update("update product set image_url=? where id=?", props.normalizedPublicPath() + "/" + nameB, b.productId());
            byte[] backup = exportBackup(a.id());
            assertThat(zipEntries(backup).keySet()).contains("images/products/" + nameA).doesNotContain("images/products/" + nameB);
            tenants.toggle(a.id()); backups.importArchive(a.id(), archive(backup));
            String image = jdbc.queryForObject("select image_url from product where tenant_id=?", String.class, a.id());
            assertThat(image).isNotEqualTo(props.normalizedPublicPath() + "/" + nameA);
            restored = directory.resolve(image.substring(image.lastIndexOf('/')+1));
            assertThat(java.nio.file.Files.readAllBytes(restored)).isEqualTo(png);
            assertThat(java.nio.file.Files.readAllBytes(directory.resolve(nameB))).isEqualTo(png);
        } finally {
            java.nio.file.Files.deleteIfExists(directory.resolve(nameA)); java.nio.file.Files.deleteIfExists(directory.resolve(nameB));
            if (restored != null) java.nio.file.Files.deleteIfExists(restored);
        }
    }

    @Test void backupUsesSelectedDestinationNotManifestTenantAndDoesNotCopyPrivileges() throws Exception {
        var entries = zipEntries(exportBackup(a.id()));
        var manifest = json.readTree(entries.get("manifest.json"));
        int folio = -1;
        for (var table : manifest.get("tables")) if (table.get("name").asText().equals("sale"))
            for (int i=0; i<table.get("columns").size(); i++) if (table.get("columns").get(i).get("name").asText().equals("folio")) folio=i;
        var rows = (com.fasterxml.jackson.databind.node.ArrayNode) json.readTree(entries.get("data/sale.json"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) rows.get(0)).set(folio, json.getNodeFactory().textNode("IMPORTED-" + suffix));
        entries.put("data/sale.json", json.writeValueAsBytes(rows));
        tenants.toggle(b.id());
        backups.importArchive(b.id(), archive(zip(entries)));
        assertThat(number("select id from product where tenant_id=?", a.id())).isEqualTo(a.productId());
        assertThat(jdbc.queryForObject("select name from product where tenant_id=?", String.class, b.id())).isEqualTo(a.productName());
        long actor = number("select cashier_id from sale where tenant_id=?", b.id());
        assertThat(actor).isNotEqualTo(a.userId());
        assertThat(number("select tenant_id from app_user where id=?", actor)).isEqualTo(b.id());
        assertThat(jdbc.queryForObject("select active from app_user where id=?", Boolean.class, actor)).isFalse();
        assertThat(jdbc.queryForList("select r.name from role r join user_roles ur on ur.role_id=r.id where ur.user_id=?", String.class, actor)).containsExactly("ROLE_CAJERO");
        assertThat(jdbc.queryForObject("select active from app_user where id=?", Boolean.class, b.userId())).isTrue();
    }

    @Test void backupPreservesCreditBalancesAndRemapsPaymentApplications() throws Exception {
        authenticate(a.username(), "ROLE_ADMIN");
        var customerForm = new com.tienda.pos.customer.CustomerForm(); customerForm.setName("Backup debtor"); customerForm.setActive(true);
        long customer = customers.create(customerForm).getId();
        var charge = new com.tienda.pos.credit.CreditChargeForm();
        charge.setAmount(new BigDecimal("75")); charge.setConcept("Historical credit");
        credits.registerCharge(customer, charge);
        var payment = new com.tienda.pos.credit.CreditPaymentForm(); payment.setAmount(new BigDecimal("20")); payment.setMethod(PaymentMethod.CASH);
        credits.registerPayment(customer, payment);
        byte[] backup = exportBackup(a.id()); tenants.toggle(a.id()); backups.importArchive(a.id(), archive(backup));
        tenants.toggle(a.id()); authenticate(a.username(), "ROLE_ADMIN");
        long newCustomer = number("select id from customer where tenant_id=? and name='Backup debtor'", a.id());
        assertThat(newCustomer).isNotEqualTo(customer);
        assertThat(credits.statement(newCustomer).balance()).isEqualByComparingTo("55");
        assertThat(number("select count(*) from credit_payment_application a join credit_charge c on c.id=a.credit_charge_id join credit_payment p on p.id=a.credit_payment_id where a.tenant_id=? and c.tenant_id=? and p.tenant_id=?", a.id(), a.id(), a.id())).isEqualTo(1);
    }

    private com.fasterxml.jackson.databind.JsonNode apiLogin(String username) throws Exception {
        SecurityContextHolder.clearContext();
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void apiIsolatesStoresAndCashierSales() throws Exception {
        String token = apiLogin(a.username()).get("accessToken").asText();
        for (String path : List.of("/api/v1/products", "/api/v1/inventory/stock", "/api/v1/sales", "/api/v1/users",
                "/api/v1/cash/current", "/api/v1/cash/sessions", "/api/v1/pos",
                "/api/v1/customers", "/api/v1/categories", "/api/v1/suppliers", "/api/v1/purchases",
                "/api/v1/expenses", "/api/v1/settings", "/api/v1/credits/summary", "/api/v1/finances")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(b.username()))));
        }
        for (String path : List.of("/api/v1/products/" + b.productId(), "/api/v1/sales/" + b.folio(),
                "/api/v1/inventory/movements/" + b.movementId())) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/users/" + b.userId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
        String cashierToken = apiLogin(cashier).get("accessToken").asText();
        mvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/sales/" + a.folio()).header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cash/sessions").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }

    @Test void suspendingAndReactivatingTenantDoesNotReviveApiTokens() throws Exception {
        var login = apiLogin(a.username());
        String token = login.get("accessToken").asText();
        new TransactionTemplate(tx).executeWithoutResult(s -> em.find(Tenant.class, a.id()).setActive(false));
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        new TransactionTemplate(tx).executeWithoutResult(s -> em.find(Tenant.class, a.id()).setActive(true));
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", login.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        apiLogin(a.username());
    }

    @Test void creditPaymentsReversalAndCancellationKeepTenantStockAndCashIsolated() {
        authenticate(a.username(), "ROLE_ADMIN");
        var form = new com.tienda.pos.customer.CustomerForm();
        form.setName("Credit A"); form.setActive(true);
        Long customer = customers.create(form).getId();
        SaleRequest request = new SaleRequest(); request.setSettlementType(SettlementType.CREDIT);
        request.setCustomerId(customer); request.setPaymentMethod(PaymentMethod.CASH);
        SaleRequest.SaleLineRequest line = new SaleRequest.SaleLineRequest();
        line.setProductId(a.productId()); line.setQuantity(BigDecimal.ONE); request.setItems(List.of(line));
        var result = sales.checkout(request);
        assertThat(result.received()).isEqualByComparingTo("0");
        assertThat(result.change()).isEqualByComparingTo("0");
        Long saleId = number("select id from sale where folio=?", result.folio());
        assertThat(number("select count(*) from payment where sale_id=?", saleId)).isZero();
        assertThat(credits.statement(customer).balance()).isEqualByComparingTo("103");
        var payment = new com.tienda.pos.credit.CreditPaymentForm();
        payment.setAmount(new BigDecimal("40")); payment.setMethod(PaymentMethod.CASH);
        Long paymentId = credits.registerPayment(customer, payment).getId();
        assertThat(credits.statement(customer).balance()).isEqualByComparingTo("63");
        assertThatThrownBy(() -> credits.cancelCredit(saleId, "error")).isInstanceOf(DomainException.class);
        authenticate(b.username(), "ROLE_ADMIN");
        assertThatThrownBy(() -> credits.statement(customer)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> credits.reversePayment(paymentId, "cross store")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> credits.cancelCredit(saleId, "cross store")).isInstanceOf(DomainException.class);
        authenticate(a.username(), "ROLE_ADMIN");
        credits.reversePayment(paymentId, "error");
        credits.cancelCredit(saleId, "error");
        assertThat(credits.statement(customer).balance()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select quantity from inventory_stock where tenant_id=? and product_id=?", BigDecimal.class, a.id(), a.productId())).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select quantity from inventory_stock where tenant_id=? and product_id=?", BigDecimal.class, b.id(), b.productId())).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("select sum(amount) from cash_movement where tenant_id=?", BigDecimal.class, a.id())).isEqualByComparingTo("103");
    }

    @Test void idempotentCreditChargesAreScopedToCustomerAndTenant() throws Exception {
        authenticate(a.username(), "ROLE_ADMIN");
        var form = new com.tienda.pos.customer.CustomerForm(); form.setName("Debtor"); form.setActive(true);
        Long customer = customers.create(form).getId();
        String token = apiLogin(a.username()).get("accessToken").asText();
        String path = "/api/v1/credits/customers/" + customer + "/charges";
        String first = mvc.perform(post(path).header("Authorization", "Bearer " + token).header("Idempotency-Key", "same-key")
                .contentType("application/json").content("{\"amount\":25,\"concept\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(path).header("Authorization", "Bearer " + token).header("Idempotency-Key", "same-key")
                .contentType("application/json").content("{\"amount\":25,\"concept\":\"Test\"}"))
                .andExpect(status().isCreated()).andExpect(content().json(first));
        mvc.perform(post(path).header("Authorization", "Bearer " + token).header("Idempotency-Key", "same-key")
                .contentType("application/json").content("{\"amount\":26,\"concept\":\"Test\"}"))
                .andExpect(status().isConflict());
        authenticate(a.username(), "ROLE_ADMIN");
        assertThat(credits.statement(customer).balance()).isEqualByComparingTo("25");
        String otherToken = apiLogin(b.username()).get("accessToken").asText();
        mvc.perform(post(path).header("Authorization", "Bearer " + otherToken).header("Idempotency-Key", "same-key")
                .contentType("application/json").content("{\"amount\":25,\"concept\":\"Test\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void centralAutomationTokenSupportsConcurrentCatalogPaginationWithoutTenantLeaks() throws Exception {
        AutomationFixture fixture = automationFixture();
        List<AutomationRequest> requests = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/eligible", "default", null));
            requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/default/groups", "Grupo default", "Grupo tienda-teo"));
            requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/tienda-teo/groups", "Grupo tienda-teo", "Grupo default"));
            requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/default/products/promotions?page=" + (round % 3) + "&size=1",
                    "Default " + ((round % 3) + 1), "Teo "));
            requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/tienda-teo/products/promotions?page=" + (round % 3) + "&size=1",
                    "Teo " + ((round % 3) + 1), "Default "));
            for (int page = 0; page < 3; page++) {
                requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/default/products/catalog?page=" + page + "&size=1",
                        "Default " + (page + 1), "Teo "));
                requests.add(new AutomationRequest("/api/automation/v1/whatsapp/stores/tienda-teo/products/catalog?page=" + page + "&size=1",
                        "Teo " + (page + 1), "Default "));
            }
        }

        var executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = requests.stream().map(request -> executor.submit(() -> {
                start.await();
                return mvc.perform(get(request.path()).header("Authorization", "Bearer " + fixture.rawToken())).andReturn();
            })).toList();
            start.countDown();
            for (int i = 0; i < results.size(); i++) {
                MvcResult result = results.get(i).get();
                AutomationRequest request = requests.get(i);
                assertThat(result.getResponse().getStatus()).as(request.path()).isEqualTo(200);
                assertThat(result.getResponse().getContentAsString()).contains(request.expected());
                if (request.excluded() != null) {
                    assertThat(result.getResponse().getContentAsString()).doesNotContain(request.excluded());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select last_used_at from platform_automation_token where id=?", LocalDateTime.class, fixture.tokenId()))
                .isNotNull();
        assertThat(jdbc.queryForObject("select version from platform_automation_token where id=?", Long.class, fixture.tokenId()))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_automation_audit where token_id=?", Long.class, fixture.tokenId()))
                .isEqualTo((long) requests.size());
    }

    private AutomationFixture automationFixture() {
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            com.tienda.pos.tenant.Tenant defaultTenant = automationTenant("default", "Default");
            com.tienda.pos.tenant.Tenant teoTenant = automationTenant("tienda-teo", "Tienda Teo");
            com.tienda.pos.whatsapp.WhatsappProvider provider = new com.tienda.pos.whatsapp.WhatsappProvider();
            provider.setName("Automation provider " + suffix); provider.setBaseUrl("http://localhost:21465");
            provider.setEncryptedSecret("test"); provider.setEnabled(true);
            provider.setStatus(com.tienda.pos.whatsapp.ProviderStatus.AVAILABLE); provider.setLastCheckedAt(LocalDateTime.now());
            em.persist(provider);
            automationTenantData(defaultTenant, provider, "Default", "Grupo default", "5215550000001");
            automationTenantData(teoTenant, provider, "Teo", "Grupo tienda-teo", "5215550000002");
        });
        var generated = automationTokens.generate("concurrency-" + suffix,
                Set.of(com.tienda.pos.whatsapp.automation.AutomationScope.ANNOUNCEMENTS_READ,
                        com.tienda.pos.whatsapp.automation.AutomationScope.ANNOUNCEMENTS_SEND));
        return new AutomationFixture(generated.value(), generated.token().getId());
    }

    private com.tienda.pos.tenant.Tenant automationTenant(String code, String name) {
        com.tienda.pos.tenant.Tenant tenant = new com.tienda.pos.tenant.Tenant();
        tenant.setCode(code); tenant.setName(name); tenant.setActive(true); em.persist(tenant);
        return tenant;
    }

    private void automationTenantData(com.tienda.pos.tenant.Tenant tenant,
                                      com.tienda.pos.whatsapp.WhatsappProvider provider,
                                      String productPrefix, String groupName, String phone) {
        com.tienda.pos.whatsapp.TenantWhatsappConnection connection = new com.tienda.pos.whatsapp.TenantWhatsappConnection();
        connection.setTenant(tenant); connection.setProvider(provider); connection.setSessionName("session-" + tenant.getCode());
        connection.setEncryptedSessionToken("test"); connection.setPhoneNumber(phone);
        connection.setStatus(com.tienda.pos.whatsapp.WhatsappSessionStatus.CONNECTED);
        connection.setLastConnectedAt(LocalDateTime.now()); connection.setLastCheckedAt(LocalDateTime.now()); em.persist(connection);

        com.tienda.pos.whatsapp.TenantWhatsappAnnouncementSettings settings = new com.tienda.pos.whatsapp.TenantWhatsappAnnouncementSettings();
        settings.setTenant(tenant); settings.setEnabled(true); settings.setConfirmedConnectionId(connection.getId());
        settings.setConfirmedProviderId(provider.getId()); settings.setConfirmedPhoneNumber(phone); settings.setConfirmedAt(LocalDateTime.now());
        em.persist(settings);

        com.tienda.pos.whatsapp.WhatsappGroup group = new com.tienda.pos.whatsapp.WhatsappGroup();
        group.setTenant(tenant); group.setConnection(connection); group.setWhatsappId(tenant.getCode() + "@g.us");
        group.setName(groupName); group.setAvailable(true); group.setSelectedForAnnouncements(true); group.setRefreshedAt(LocalDateTime.now());
        em.persist(group);

        for (int index = 1; index <= 3; index++) {
            Product product = new Product(); product.setTenant(tenant); product.setCode("AUTO-" + tenant.getCode() + "-" + index);
            product.setName(productPrefix + " " + index); product.setPurchaseCost(new BigDecimal("5.00"));
            product.setSalePrice(new BigDecimal("10.00")); product.setCurrentStock(BigDecimal.TEN);
            product.setPromocionWhatsapp(true); product.setActive(true); em.persist(product);
        }
    }

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

    @Test void productsPaginateBeyondTwentyWithoutExposingOtherTenants() throws Exception {
        addProducts(44);
        MockHttpSession session = login(a.username(), "/admin");
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            MvcResult result = mvc.perform(get("/admin/products").param("page", String.valueOf(index)).session(session))
                    .andExpect(status().isOk()).andExpect(view().name("products/index"))
                    .andExpect(content().string(containsString("data-products-table")))
                    .andExpect(content().string(not(containsString(b.productName())))).andReturn();
            var page = productPage(result);
            assertThat(page.getTotalElements()).isEqualTo(45);
            assertThat(page.getTotalPages()).isEqualTo(3);
            assertThat(page.getNumber()).isEqualTo(index);
            assertThat(page.getNumberOfElements()).isEqualTo(index < 2 ? 20 : 5);
            for (Product product : page) {
                assertThat(product.getTenant().getId()).isEqualTo(a.id());
                assertThat(seen.add(product.getId())).isTrue();
            }
        }
        assertThat(seen).hasSize(45).contains(a.productId()).doesNotContain(b.productId());
        mvc.perform(get("/admin/api/products/table").param("start", "40").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsTotal").value(45))
                .andExpect(jsonPath("$.recordsFiltered").value(45))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test void productSearchKeepsQueryInPaginationAndRemainsTenantScoped() throws Exception {
        addProducts(44);
        jdbc.update("update product set name=? where id=?", "Paged & cafe other tenant", b.productId());
        MockHttpSession session = login(a.username(), "/admin");
        MvcResult result = mvc.perform(get("/admin/products").param("q", "  Paged & cafe  ").param("page", "1").session(session))
                .andExpect(status().isOk()).andExpect(model().attribute("q", "Paged & cafe"))
                .andExpect(content().string(containsString("value=\"Paged &amp; cafe\"")))
                .andExpect(content().string(containsString("name=\"page\" value=\"0\"")))
                .andExpect(content().string(containsString("data-product-filter-form")))
                .andExpect(content().string(not(containsString("other tenant")))).andReturn();
        var page = productPage(result);
        assertThat(page.getTotalElements()).isEqualTo(44);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(20).allSatisfy(product -> assertThat(product.getTenant().getId()).isEqualTo(a.id()));
        for (String query : List.of("SAME-CODE", "1234567890123")) {
            var matching = productPage(mvc.perform(get("/admin/products").param("q", query).session(session))
                    .andExpect(status().isOk()).andReturn());
            assertThat(matching.getTotalElements()).isEqualTo(1);
            assertThat(matching.getContent()).extracting(Product::getId).containsExactly(a.productId());
        }
    }

    @Test void productPaginationHandlesNegativeStaleAndEmptyPages() throws Exception {
        addProducts(24);
        MockHttpSession session = login(a.username(), "/admin");
        for (int requested : List.of(-1, 99)) {
            var page = productPage(mvc.perform(get("/admin/products").param("page", String.valueOf(requested)).session(session))
                    .andExpect(status().isOk()).andReturn());
            assertThat(page.getNumber()).isEqualTo(requested < 0 ? 0 : 1);
            assertThat(page.getNumberOfElements()).isEqualTo(requested < 0 ? 20 : 5);
        }
        MvcResult empty = mvc.perform(get("/admin/products").param("q", "no-match-" + suffix).param("page", "99").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No hay productos para mostrar.")))
                .andExpect(content().string(not(containsString("class=\"product-pagination-links\"")))).andReturn();
        assertThat(productPage(empty).getTotalElements()).isZero();
        assertThat(productPage(empty).getNumber()).isZero();
    }

    @Test void whatsappPromotionTogglePersistsAndDoesNotChangeCatalogPromotion() throws Exception {
        MockHttpSession session = login(a.username(), "/admin");
        assertThat(jdbc.queryForObject("select promocion_whatsapp from product where id=?", Boolean.class, a.productId())).isFalse();
        assertThat(jdbc.queryForObject("select promoted from product where id=?", Boolean.class, a.productId())).isFalse();

        mvc.perform(post("/admin/products/{id}/whatsapp-promotion", a.productId()).session(session).with(csrf())
                        .param("selected", "true").param("q", "SAME-CODE").param("page", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products?q=SAME-CODE&name=&brand=&sort=name&direction=asc&page=0"));

        assertThat(jdbc.queryForObject("select promocion_whatsapp from product where id=?", Boolean.class, a.productId())).isTrue();
        assertThat(jdbc.queryForObject("select promoted from product where id=?", Boolean.class, a.productId())).isFalse();
        mvc.perform(get("/admin/products").param("q", "SAME-CODE").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Promoción WhatsApp")))
                .andExpect(content().string(containsString("Seleccionado")));

        mvc.perform(post("/admin/products/{id}/whatsapp-promotion", a.productId()).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select promocion_whatsapp from product where id=?", Boolean.class, a.productId())).isFalse();
        assertThat(jdbc.queryForObject("select promoted from product where id=?", Boolean.class, a.productId())).isFalse();
    }

    @Test void tableFiltersSuggestionsAndCountsNeverExposeAnotherTenant() throws Exception {
        addProducts(24);
        jdbc.update("update product set brand=?, promocion_whatsapp=true where id=?", "Own brand", a.productId());
        jdbc.update("update product set brand=? where id=?", "Private brand", b.productId());
        MockHttpSession session = login(a.username(), "/admin");
        mvc.perform(get("/admin/api/products/table").session(session).param("tenantId", b.id().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsTotal").value(25))
                .andExpect(jsonPath("$.recordsFiltered").value(25)).andExpect(jsonPath("$.data.length()").value(20))
                .andExpect(jsonPath("$.columnControl.brand[*].value").value(org.hamcrest.Matchers.contains("Own brand")))
                .andExpect(content().string(not(containsString("Private brand"))));
        mvc.perform(get("/admin/api/products/table").session(session).param("q", "SAME-CODE")
                        .param("brand", "Own").param("active", "true").param("whatsapp", "true")
                        .param("minPrice", "200").param("maxPrice", "100").param("draw", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draw").value(4))
                .andExpect(jsonPath("$.recordsTotal").value(25)).andExpect(jsonPath("$.recordsFiltered").value(1))
                .andExpect(jsonPath("$.data[0].id").value(a.productId().intValue()));
        mvc.perform(get("/admin/api/products/table").session(session)
                        .param("columns[2][columnControl][list][0]", "Own brand"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsFiltered").value(1));
        mvc.perform(get("/admin/api/products/table").session(session).param("brand", "Private brand"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordsFiltered").value(0));
        mvc.perform(get("/admin/api/products/suggestions").session(session).param("field", "brand"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("Own brand")));
        mvc.perform(get("/admin/api/products/suggestions").session(session).param("field", "name").param("q", "Product-"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains(a.productName())));
        mvc.perform(get("/admin/api/products/table").session(session).param("order[0][column]", "3")
                        .param("order[0][dir]", "desc").param("length", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(a.productId().intValue()))
                .andExpect(jsonPath("$.data.length()").value(25));
        mvc.perform(get("/admin/api/products/table").session(session).param("start", "-100").param("length", "-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(10));
    }

    @Test void interactiveProductEndpointsStillRequireAnActiveAdministrator() throws Exception {
        for (String path : List.of("/admin/api/products/table", "/admin/api/products/suggestions?field=name"))
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        MockHttpSession seller = login(cashier, "/admin/pos");
        for (String path : List.of("/admin/api/products/table", "/admin/api/products/suggestions?field=name"))
            mvc.perform(get(path).session(seller)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/products/{id}/whatsapp-promotion", a.productId()).session(seller).with(csrf())
                .param("selected", "true")).andExpect(status().isForbidden());
        MockHttpSession admin = login(a.username(), "/admin");
        mvc.perform(post("/admin/products/{id}/whatsapp-promotion", b.productId()).session(admin).with(csrf())
                .param("selected", "true")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select promocion_whatsapp from product where id=?", Boolean.class, b.productId())).isFalse();
        jdbc.update("update tenant set active=false where id=?", a.id());
        mvc.perform(get("/admin/api/products/table").session(admin)).andExpect(redirectedUrl("/admin/login?expired"));
    }

    private void addProducts(int count) {
        authenticate(a.username(), "ROLE_ADMIN");
        for (int index = 0; index < count; index++) {
            ProductForm form = new ProductForm();
            // Repeated names exercise the stable ID tie-breaker between pages.
            form.setName("Paged & cafe " + String.format("%02d", index / 2));
            form.setCode("PAGED-" + index); form.setBarcode("998800" + String.format("%07d", index));
            form.setPurchaseCost(new BigDecimal("10")); form.setSalePrice(new BigDecimal("20"));
            products.save(form);
        }
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private org.springframework.data.domain.Page<Product> productPage(MvcResult result) {
        return (org.springframework.data.domain.Page<Product>) result.getModelAndView().getModel().get("products");
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
