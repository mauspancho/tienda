package com.tienda.pos.sale;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.cash.CashMovement;
import com.tienda.pos.cash.CashMovementRepository;
import com.tienda.pos.cash.CashMovementType;
import com.tienda.pos.cash.CashRegister;
import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.cash.CashRegisterSessionRepository;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.customer.CustomerRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.inventory.InventoryStock;
import com.tienda.pos.payment.Payment;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

@Service
@NormalMode
public class SaleService {

    private static final DateTimeFormatter FOLIO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final InventoryService inventoryService;
    private final CashRegisterSessionRepository cashRegisterSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final StoreContextService storeContextService;
    private final CurrentTenant currentTenant;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository,
                       CustomerRepository customerRepository, AppUserRepository userRepository,
                       InventoryService inventoryService, CashRegisterSessionRepository cashRegisterSessionRepository,
                       CashMovementRepository cashMovementRepository, StoreContextService storeContextService,
                       CurrentTenant currentTenant) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.cashRegisterSessionRepository = cashRegisterSessionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.storeContextService = storeContextService;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public SaleResult checkout(SaleRequest request) {
        Tenant tenant = currentTenant.get();
        Long tenantId = tenant.getId();
        AppUser cashier = userRepository.findByUsernameWithTenant(CurrentUser.username())
                .filter(user -> user.getTenant() != null && user.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new DomainException("No se encontró el cajero actual."));
        CashRegisterSession cashSession = cashRegisterSessionRepository.findByTenantIdAndCashierAndOpenTrue(tenantId, cashier)
                .orElseThrow(() -> new DomainException("Abre la caja antes de realizar una venta."));
        CashRegister cashRegister = cashSession.getCashRegister() == null
                ? storeContextService.defaultCashRegister()
                : cashSession.getCashRegister();
        Branch branch = cashSession.getBranch() == null ? cashRegister.getBranch() : cashSession.getBranch();

        Sale sale = new Sale();
        sale.setTenant(tenant);
        sale.setFolio("V" + LocalDateTime.now().format(FOLIO_FORMAT));
        sale.setCashier(cashier);
        sale.setBranch(branch);
        sale.setCashRegister(cashRegister);
        if (request.getCustomerId() != null) {
            sale.setCustomer(customerRepository.findByIdAndTenantId(request.getCustomerId(), tenantId).orElse(null));
        } else {
            customerRepository.findFirstByTenantIdAndName(tenantId, "Público General").ifPresent(sale::setCustomer);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleRequest.SaleLineRequest line : request.getItems().stream()
                .sorted(Comparator.comparing(SaleRequest.SaleLineRequest::getProductId))
                .toList()) {
            Product product = productRepository.findByIdAndTenantIdForUpdate(line.getProductId(), tenantId)
                    .orElseThrow(() -> new DomainException("Producto no encontrado."));
            InventoryStock stock = inventoryService.defaultStockForUpdate(product);
            if (!product.isActive()) {
                throw new DomainException("Producto inactivo: " + product.getName());
            }
            if (line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("La cantidad debe ser mayor a cero.");
            }
            if (product.getSalePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Precio inválido: " + product.getName());
            }
            if (stock.getQuantity().compareTo(line.getQuantity()) < 0) {
                throw new DomainException("No hay suficiente inventario de " + product.getName());
            }
            BigDecimal itemSubtotal = MoneyUtils.money(product.getSalePrice().multiply(line.getQuantity()));
            BigDecimal itemProfit = MoneyUtils.money(product.getSalePrice().subtract(product.getPurchaseCost()).multiply(line.getQuantity()));
            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setProductNameSnapshot(product.getName());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(MoneyUtils.money(product.getSalePrice()));
            item.setUnitCost(MoneyUtils.money(product.getPurchaseCost()));
            item.setSubtotal(itemSubtotal);
            item.setProfit(itemProfit);
            sale.addItem(item);
            subtotal = subtotal.add(itemSubtotal);

            BigDecimal previous = stock.getQuantity();
            BigDecimal next = previous.subtract(line.getQuantity());
            stock.setQuantity(next);
            stock.setMinimumStock(product.getMinimumStock());
            product.setCurrentStock(next);
            productRepository.save(product);
            inventoryService.saveStock(stock);
            inventoryService.createMovement(product, InventoryMovementType.SALE, line.getQuantity().negate(),
                    previous, next, "SALE", null, "Venta " + sale.getFolio());
        }

        BigDecimal discount = MoneyUtils.money(request.getDiscount());
        BigDecimal total = MoneyUtils.money(subtotal.subtract(discount));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("El descuento no puede ser mayor al subtotal.");
        }
        BigDecimal received = total;

        sale.setSubtotal(MoneyUtils.money(subtotal));
        sale.setDiscount(discount);
        sale.setTotal(total);
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(request.getPaymentMethod());
        payment.setAmount(total);
        payment.setReceivedAmount(received);
        payment.setChangeAmount(BigDecimal.ZERO);
        sale.setPayment(payment);
        Sale saved = saleRepository.save(sale);

        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            CashMovement movement = new CashMovement();
            movement.setTenant(tenant);
            movement.setCashRegisterSession(cashSession);
            movement.setType(CashMovementType.SALE);
            movement.setAmount(total);
            movement.setReferenceType("SALE");
            movement.setReferenceId(saved.getId());
            movement.setUser(cashier);
            cashMovementRepository.save(movement);
        }
        return new SaleResult(saved.getFolio(), total, received, BigDecimal.ZERO);
    }

    public BigDecimal calculateChange(BigDecimal total, BigDecimal received) {
        if (received.compareTo(total) < 0) {
            throw new DomainException("El efectivo recibido debe cubrir el total.");
        }
        return MoneyUtils.money(received.subtract(total));
    }
}