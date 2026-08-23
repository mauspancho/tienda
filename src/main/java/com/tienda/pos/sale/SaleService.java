package com.tienda.pos.sale;

import com.tienda.pos.cash.CashMovement;
import com.tienda.pos.cash.CashMovementRepository;
import com.tienda.pos.cash.CashMovementType;
import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.cash.CashRegisterSessionRepository;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.customer.CustomerRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.payment.Payment;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
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

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository,
                       CustomerRepository customerRepository, AppUserRepository userRepository,
                       InventoryService inventoryService, CashRegisterSessionRepository cashRegisterSessionRepository,
                       CashMovementRepository cashMovementRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.cashRegisterSessionRepository = cashRegisterSessionRepository;
        this.cashMovementRepository = cashMovementRepository;
    }

    @Transactional
    public SaleResult checkout(SaleRequest request) {
        AppUser cashier = userRepository.findByUsername(CurrentUser.username())
                .orElseThrow(() -> new DomainException("No se encontró el cajero actual."));
        CashRegisterSession cashSession = cashRegisterSessionRepository.findByCashierAndOpenTrue(cashier)
                .orElseThrow(() -> new DomainException("Abre la caja antes de realizar una venta."));

        Sale sale = new Sale();
        sale.setFolio("V" + LocalDateTime.now().format(FOLIO_FORMAT));
        sale.setCashier(cashier);
        if (request.getCustomerId() != null) {
            sale.setCustomer(customerRepository.findById(request.getCustomerId()).orElse(null));
        } else {
            customerRepository.findFirstByName("Público General").ifPresent(sale::setCustomer);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleRequest.SaleLineRequest line : request.getItems().stream()
                .sorted(Comparator.comparing(SaleRequest.SaleLineRequest::getProductId))
                .toList()) {
            Product product = productRepository.findByIdForUpdate(line.getProductId())
                    .orElseThrow(() -> new DomainException("Producto no encontrado."));
            if (!product.isActive()) {
                throw new DomainException("Producto inactivo: " + product.getName());
            }
            if (line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("La cantidad debe ser mayor a cero.");
            }
            if (product.getSalePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Precio inválido: " + product.getName());
            }
            if (product.getCurrentStock().compareTo(line.getQuantity()) < 0) {
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

            BigDecimal previous = product.getCurrentStock();
            BigDecimal next = previous.subtract(line.getQuantity());
            product.setCurrentStock(next);
            productRepository.save(product);
            inventoryService.createMovement(product, InventoryMovementType.SALE, line.getQuantity().negate(),
                    previous, next, "SALE", null, "Venta " + sale.getFolio());
        }

        BigDecimal discount = MoneyUtils.money(request.getDiscount());
        BigDecimal total = MoneyUtils.money(subtotal.subtract(discount));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("El descuento no puede ser mayor al subtotal.");
        }
        BigDecimal received = request.getPaymentMethod() == PaymentMethod.CASH
                ? MoneyUtils.money(request.getReceivedAmount())
                : total;
        if (request.getPaymentMethod() == PaymentMethod.CASH && received.compareTo(total) < 0) {
            throw new DomainException("El efectivo recibido debe cubrir el total.");
        }

        sale.setSubtotal(MoneyUtils.money(subtotal));
        sale.setDiscount(discount);
        sale.setTotal(total);
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(request.getPaymentMethod());
        payment.setAmount(total);
        payment.setReceivedAmount(received);
        payment.setChangeAmount(received.subtract(total));
        sale.setPayment(payment);
        Sale saved = saleRepository.save(sale);

        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            CashMovement movement = new CashMovement();
            movement.setCashRegisterSession(cashSession);
            movement.setType(CashMovementType.SALE);
            movement.setAmount(total);
            movement.setReferenceType("SALE");
            movement.setReferenceId(saved.getId());
            movement.setUser(cashier);
            cashMovementRepository.save(movement);
        }
        return new SaleResult(saved.getFolio(), total, received, received.subtract(total));
    }

    public BigDecimal calculateChange(BigDecimal total, BigDecimal received) {
        if (received.compareTo(total) < 0) {
            throw new DomainException("El efectivo recibido debe cubrir el total.");
        }
        return MoneyUtils.money(received.subtract(total));
    }
}
