package com.tienda.pos.sale;

import com.tienda.pos.cash.CashRegister;
import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.hardening.HardeningTestConfiguration;
import com.tienda.pos.inventory.InventoryStock;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.product.Product;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.warehouse.Warehouse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitWebConfig(HardeningTestConfiguration.class)
@TestPropertySource("/hardening-test.properties")
class SaleCheckoutIntegrationTest {

    @Autowired private SaleService saleService;
    @Autowired private StoreContextService storeContext;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    private Fixture fixture;

    @BeforeEach
    void createCommittedFixture() {
        String username = "cashier-" + UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "", List.of()));
        fixture = new TransactionTemplate(transactionManager).execute(status -> {
            Tenant tenant = new Tenant();
            tenant.setCode(username);
            tenant.setName("Checkout test");
            entityManager.persist(tenant);
            AppUser cashier = new AppUser();
            cashier.setTenant(tenant);
            cashier.setUsername(username);
            cashier.setPasswordHash("test-only");
            cashier.setFirstName("Test");
            cashier.setLastName("Cashier");
            entityManager.persist(cashier);
            CashRegister register = storeContext.defaultCashRegister();
            Warehouse warehouse = storeContext.defaultWarehouse();
            CashRegisterSession session = new CashRegisterSession();
            session.setTenant(tenant);
            session.setCashier(cashier);
            session.setCashRegister(register);
            session.setBranch(register.getBranch());
            entityManager.persist(session);
            Product product = new Product();
            product.setTenant(tenant);
            product.setCode("CHECKOUT");
            product.setName("Product at 103");
            product.setSalePrice(new BigDecimal("103.00"));
            product.setPurchaseCost(new BigDecimal("60.00"));
            product.setCurrentStock(BigDecimal.TEN);
            entityManager.persist(product);
            InventoryStock stock = new InventoryStock();
            stock.setTenant(tenant);
            stock.setWarehouse(warehouse);
            stock.setProduct(product);
            stock.setQuantity(BigDecimal.TEN);
            stock.setMinimumStock(BigDecimal.ZERO);
            entityManager.persist(stock);
            return new Fixture(tenant.getId(), product.getId(), stock.getId());
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cash200ForTotal103PersistsPaymentAndReturns97Change() {
        SaleResult result = saleService.checkout(request(PaymentMethod.CASH, new BigDecimal("200")));
        assertThat(result.total()).isEqualByComparingTo("103.00");
        assertThat(result.received()).isEqualByComparingTo("200.00");
        assertThat(result.change()).isEqualByComparingTo("97.00");
        assertPayment("200.00", "97.00", PaymentMethod.CASH);
        assertThat(jdbc.queryForObject("select amount from cash_movement where tenant_id = ?",
                BigDecimal.class, fixture.tenantId())).isEqualByComparingTo("103.00");
        assertStock("9.00");
    }

    @Test
    void cash100ForTotal103RejectsAndRollsBackEveryWrite() {
        assertThatThrownBy(() -> saleService.checkout(request(PaymentMethod.CASH, new BigDecimal("100"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("cubrir el total");
        assertNoCheckoutWrites();
    }

    @Test
    void exactCashHasZeroChange() {
        SaleResult result = saleService.checkout(request(PaymentMethod.CASH, new BigDecimal("103")));
        assertThat(result.change()).isEqualByComparingTo("0.00");
        assertPayment("103.00", "0.00", PaymentMethod.CASH);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void missingOrInvalidCashIsRejected(String received) {
        assertThatThrownBy(() -> saleService.checkout(request(PaymentMethod.CASH,
                received == null ? null : new BigDecimal(received))))
                .isInstanceOf(DomainException.class);
        assertNoCheckoutWrites();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CARD", "TRANSFER"})
    void nonCashUsesTotalAndNeverGeneratesCashMovement(PaymentMethod method) {
        SaleResult result = saleService.checkout(request(method, new BigDecimal("200")));
        assertThat(result.received()).isEqualByComparingTo("103.00");
        assertThat(result.change()).isEqualByComparingTo("0.00");
        assertPayment("103.00", "0.00", method);
        assertThat(count("cash_movement")).isZero();
    }

    private SaleRequest request(PaymentMethod method, BigDecimal received) {
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(method);
        request.setReceivedAmount(received);
        SaleRequest.SaleLineRequest line = new SaleRequest.SaleLineRequest();
        line.setProductId(fixture.productId());
        line.setQuantity(BigDecimal.ONE);
        request.setItems(List.of(line));
        return request;
    }

    private void assertPayment(String received, String change, PaymentMethod method) {
        var payment = jdbc.queryForMap("""
                select p.amount, p.received_amount, p.change_amount, p.method
                from payment p join sale s on s.id = p.sale_id where s.tenant_id = ?
                """, fixture.tenantId());
        assertThat((BigDecimal) payment.get("AMOUNT")).isEqualByComparingTo("103.00");
        assertThat((BigDecimal) payment.get("RECEIVED_AMOUNT")).isEqualByComparingTo(received);
        assertThat((BigDecimal) payment.get("CHANGE_AMOUNT")).isEqualByComparingTo(change);
        assertThat(payment.get("METHOD")).isEqualTo(method.name());
    }

    private void assertNoCheckoutWrites() {
        assertStock("10.00");
        assertThat(count("sale")).isZero();
        assertThat(count("cash_movement")).isZero();
        assertThat(count("inventory_movement")).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from payment p join sale s on s.id = p.sale_id where s.tenant_id = ?
                """, Long.class, fixture.tenantId())).isZero();
    }

    private void assertStock(String expected) {
        assertThat(jdbc.queryForObject("select current_stock from product where id = ?",
                BigDecimal.class, fixture.productId())).isEqualByComparingTo(expected);
        assertThat(jdbc.queryForObject("select quantity from inventory_stock where id = ?",
                BigDecimal.class, fixture.stockId())).isEqualByComparingTo(expected);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where tenant_id = ?",
                Long.class, fixture.tenantId());
    }

    private record Fixture(Long tenantId, Long productId, Long stockId) {
    }
}
