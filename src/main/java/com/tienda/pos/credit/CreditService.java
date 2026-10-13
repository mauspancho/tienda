package com.tienda.pos.credit;

import com.tienda.pos.audit.AuditLog;
import com.tienda.pos.audit.AuditLogRepository;
import com.tienda.pos.cash.*;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.customer.Customer;
import com.tienda.pos.customer.CustomerRepository;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.inventory.InventoryMovementType;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.sale.*;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static com.tienda.pos.credit.CreditViews.*;

@Service
@NormalMode
public class CreditService {
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final CreditPaymentRepository paymentRepository;
    private final CreditPaymentApplicationRepository applicationRepository;
    private final CreditChargeRepository chargeRepository;
    private final AppUserRepository userRepository;
    private final CashRegisterSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AuditLogRepository auditRepository;

    public CreditService(CustomerRepository customerRepository, SaleRepository saleRepository,
                         CreditPaymentRepository paymentRepository,
                         CreditPaymentApplicationRepository applicationRepository,
                         CreditChargeRepository chargeRepository,
                         AppUserRepository userRepository, CashRegisterSessionRepository cashSessionRepository,
                         CashMovementRepository cashMovementRepository, ProductRepository productRepository,
                         InventoryService inventoryService, AuditLogRepository auditRepository) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.applicationRepository = applicationRepository;
        this.chargeRepository = chargeRepository;
        this.userRepository = userRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        BigDecimal granted = money(saleRepository.totalCreditSales().add(chargeRepository.totalActiveCharges()));
        BigDecimal paid = money(paymentRepository.totalActivePayments());
        List<CustomerRow> rows = customerRows("", "ALL", "DEBT");
        return new Summary(money(granted.subtract(paid)), rows.stream().filter(r -> r.balance().signum() > 0).count(), granted, paid);
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> customerRows(String query, String filter, String sort) {
        String term = query == null ? "" : query.trim();
        List<Customer> customers = customerRepository.search(term);
        List<CustomerRow> rows = customers.stream()
                .filter(c -> !"Público General".equalsIgnoreCase(c.getName()))
                .map(this::customerRow)
                .filter(row -> switch (filter == null ? "DEBT" : filter) {
                    case "WITHOUT_DEBT" -> row.balance().signum() == 0;
                    case "ALL" -> true;
                    default -> row.balance().signum() > 0;
                }).toList();
        Comparator<CustomerRow> comparator = switch (sort == null ? "DEBT" : sort) {
            case "NAME" -> Comparator.comparing(r -> r.customer().getFullName(), String.CASE_INSENSITIVE_ORDER);
            case "ACTIVITY" -> Comparator.comparing(CustomerRow::lastActivity,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(CustomerRow::balance).reversed();
        };
        return rows.stream().sorted(comparator).toList();
    }

    @Transactional(readOnly = true)
    public Statement statement(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        List<Sale> sales = saleRepository.findByCustomerIdAndSettlementTypeOrderBySaleDateAsc(customerId, SettlementType.CREDIT);
        List<CreditPayment> payments = paymentRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        List<CreditCharge> charges = chargeRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        List<ChargeRow> chargeRows = charges.stream().map(charge -> {
            BigDecimal applied = money(applicationRepository.activeAppliedToCharge(charge.getId()));
            BigDecimal balance = charge.getStatus() == CreditChargeStatus.ACTIVE
                    ? money(charge.getAmount().subtract(applied)) : money(BigDecimal.ZERO);
            return new ChargeRow(charge, applied, balance);
        }).toList();
        List<SaleRow> saleRows = sales.stream().map(sale -> {
            BigDecimal paid = money(applicationRepository.activeAppliedToSale(sale.getId()));
            BigDecimal balance = sale.getStatus() == SaleStatus.COMPLETED ? money(sale.getTotal().subtract(paid)) : money(BigDecimal.ZERO);
            return new SaleRow(sale, paid, balance);
        }).toList();
        BigDecimal granted = money(sales.stream().filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                .map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(charges.stream().filter(c -> c.getStatus() == CreditChargeStatus.ACTIVE)
                        .map(CreditCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        BigDecimal paid = money(payments.stream().filter(p -> p.getStatus() == CreditPaymentStatus.ACTIVE)
                .map(CreditPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Statement(customer, money(granted.subtract(paid)), granted, paid, saleRows, chargeRows, payments,
                movements(sales, charges, payments));
    }

    @Transactional
    public CreditPayment registerPayment(Long customerId, CreditPaymentForm form) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        BigDecimal amount = money(form.getAmount());
        if (amount.signum() <= 0) throw new DomainException("El abono debe ser mayor que cero.");
        List<Sale> sales = saleRepository.findByCustomerIdAndSettlementTypeOrderBySaleDateAsc(customerId, SettlementType.CREDIT)
                .stream().filter(s -> s.getStatus() == SaleStatus.COMPLETED).toList();
        List<CreditCharge> charges = chargeRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .filter(c -> c.getStatus() == CreditChargeStatus.ACTIVE).toList();
        List<Obligation> obligations = new ArrayList<>();
        for (Sale sale : sales) {
            obligations.add(new Obligation(sale.getSaleDate(), sale, null,
                    money(sale.getTotal().subtract(applicationRepository.activeAppliedToSale(sale.getId())))));
        }
        for (CreditCharge charge : charges) {
            obligations.add(new Obligation(charge.getCreatedAt(), null, charge,
                    money(charge.getAmount().subtract(applicationRepository.activeAppliedToCharge(charge.getId())))));
        }
        obligations.sort(Comparator.comparing(Obligation::date));
        BigDecimal currentBalance = money(obligations.stream().map(Obligation::pending).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (amount.compareTo(currentBalance) > 0) throw new DomainException("El abono no puede superar el saldo pendiente.");

        AppUser user = currentUser();
        CashRegisterSession cashSession = null;
        if (form.getMethod() == PaymentMethod.CASH) {
            cashSession = cashSessionRepository.findByCashierAndOpenTrue(user)
                    .orElseThrow(() -> new DomainException("Abre la caja antes de recibir un abono en efectivo."));
        }
        CreditPayment payment = new CreditPayment();
        payment.setCustomer(customer);
        payment.setMethod(form.getMethod());
        payment.setAmount(amount);
        payment.setNotes(clean(form.getNotes()));
        payment.setUser(user);
        payment.setCreatedBy(user.getUsername());
        BigDecimal remaining = amount;
        for (Obligation obligation : obligations) {
            BigDecimal pending = obligation.pending();
            if (remaining.signum() <= 0 || pending.signum() <= 0) continue;
            BigDecimal applied = remaining.min(pending);
            CreditPaymentApplication application = new CreditPaymentApplication();
            application.setSale(obligation.sale());
            application.setCharge(obligation.charge());
            application.setAmount(applied);
            application.setCreatedBy(user.getUsername());
            payment.addApplication(application);
            remaining = remaining.subtract(applied);
        }
        if (remaining.signum() != 0) throw new DomainException("No fue posible distribuir el abono.");
        CreditPayment saved = paymentRepository.save(payment);
        if (cashSession != null) {
            CashMovement movement = new CashMovement();
            movement.setCashRegisterSession(cashSession);
            movement.setType(CashMovementType.CREDIT_PAYMENT);
            movement.setAmount(amount);
            movement.setReferenceType("CREDIT_PAYMENT");
            movement.setReferenceId(saved.getId());
            movement.setNotes("Abono de " + customer.getFullName());
            movement.setUser(user);
            cashMovementRepository.save(movement);
        }
        audit("CREDIT_PAYMENT_CREATED", "CreditPayment", saved.getId(), "Abono " + amount + " para cliente #" + customerId, user);
        return saved;
    }

    @Transactional
    public CreditCharge registerCharge(Long customerId, CreditChargeForm form) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        if (!customer.isActive()) throw new DomainException("El cliente está inactivo.");
        if ("Público General".equalsIgnoreCase(customer.getName())) throw new DomainException("Público General no puede utilizar crédito.");
        BigDecimal amount = money(form.getAmount());
        if (amount.signum() <= 0) throw new DomainException("El cargo debe ser mayor que cero.");
        if (form.getConcept() == null || form.getConcept().isBlank()) throw new DomainException("Indica el concepto del cargo.");
        AppUser user = currentUser();
        CreditCharge charge = new CreditCharge();
        charge.setCustomer(customer); charge.setAmount(amount); charge.setConcept(form.getConcept().trim()); charge.setUser(user);
        charge.setCreatedBy(user.getUsername());
        CreditCharge saved = chargeRepository.save(charge);
        audit("CREDIT_CHARGE_CREATED", "CreditCharge", saved.getId(), "Cargo " + amount + ": " + saved.getConcept(), user);
        return saved;
    }

    @Transactional
    public void reverseCharge(Long chargeId, String reason) {
        requireAdmin();
        CreditCharge snapshot = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new DomainException("Cargo no encontrado."));
        customerRepository.findByIdForUpdate(snapshot.getCustomer().getId())
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        CreditCharge charge = chargeRepository.findByIdForUpdate(chargeId)
                .orElseThrow(() -> new DomainException("Cargo no encontrado."));
        if (charge.getStatus() == CreditChargeStatus.REVERSED) throw new DomainException("El cargo ya fue revertido.");
        if (applicationRepository.existsActiveByChargeId(chargeId)) throw new DomainException("Revierte primero los abonos aplicados a este cargo.");
        if (reason == null || reason.isBlank()) throw new DomainException("Indica el motivo de reversión.");
        AppUser user = currentUser();
        charge.setStatus(CreditChargeStatus.REVERSED); charge.setReversedAt(LocalDateTime.now());
        charge.setReversedBy(user.getUsername()); charge.setReversalReason(reason.trim()); charge.setUpdatedBy(user.getUsername());
        chargeRepository.save(charge);
        audit("CREDIT_CHARGE_REVERSED", "CreditCharge", chargeId, reason.trim(), user);
    }

    @Transactional
    public void reversePayment(Long paymentId, String reason) {
        requireAdmin();
        CreditPayment snapshot = paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new DomainException("Abono no encontrado."));
        customerRepository.findByIdForUpdate(snapshot.getCustomer().getId())
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        CreditPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new DomainException("Abono no encontrado."));
        if (payment.getStatus() == CreditPaymentStatus.REVERSED) throw new DomainException("El abono ya fue revertido.");
        if (reason == null || reason.isBlank()) throw new DomainException("Indica el motivo de la reversión.");
        AppUser user = currentUser();
        payment.setStatus(CreditPaymentStatus.REVERSED);
        payment.setReversedAt(LocalDateTime.now());
        payment.setReversedBy(user.getUsername());
        payment.setReversalReason(reason.trim());
        payment.setUpdatedBy(user.getUsername());
        paymentRepository.save(payment);
        if (payment.getMethod() == PaymentMethod.CASH) {
            CashMovement original = cashMovementRepository
                    .findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtAsc("CREDIT_PAYMENT", paymentId)
                    .orElseThrow(() -> new DomainException("No se encontró el movimiento de caja del abono."));
            CashMovement reversal = new CashMovement();
            reversal.setCashRegisterSession(original.getCashRegisterSession());
            reversal.setType(CashMovementType.CREDIT_PAYMENT_REVERSAL);
            reversal.setAmount(payment.getAmount().negate());
            reversal.setReferenceType("CREDIT_PAYMENT_REVERSAL");
            reversal.setReferenceId(paymentId);
            reversal.setNotes(reason.trim());
            reversal.setUser(user);
            cashMovementRepository.save(reversal);
        }
        audit("CREDIT_PAYMENT_REVERSED", "CreditPayment", paymentId, reason.trim(), user);
    }

    @Transactional
    public void cancelCredit(Long saleId, String reason) {
        requireAdmin();
        Sale snapshot = saleRepository.findById(saleId)
                .orElseThrow(() -> new DomainException("Crédito no encontrado."));
        if (snapshot.getCustomer() == null) throw new DomainException("El crédito no tiene cliente.");
        customerRepository.findByIdForUpdate(snapshot.getCustomer().getId())
                .orElseThrow(() -> new DomainException("Cliente no encontrado."));
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new DomainException("Crédito no encontrado."));
        if (sale.getSettlementType() != SettlementType.CREDIT) throw new DomainException("La venta no es un crédito.");
        if (sale.getStatus() != SaleStatus.COMPLETED) throw new DomainException("El crédito ya no está activo.");
        if (applicationRepository.existsActiveBySaleId(saleId)) {
            throw new DomainException("Revierte primero los abonos aplicados a este crédito.");
        }
        if (reason == null || reason.isBlank()) throw new DomainException("Indica el motivo de cancelación.");
        sale.getItems().stream().sorted(Comparator.comparing(i -> i.getProduct().getId())).forEach(item -> {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new DomainException("Producto no encontrado."));
            BigDecimal previous = product.getCurrentStock();
            BigDecimal next = previous.add(item.getQuantity());
            product.setCurrentStock(next);
            productRepository.save(product);
            inventoryService.createMovement(product, InventoryMovementType.SALE_RETURN, item.getQuantity(),
                    previous, next, "SALE_CANCELLATION", saleId, "Cancelación de crédito " + sale.getFolio());
        });
        AppUser user = currentUser();
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(LocalDateTime.now());
        sale.setCancelledBy(user.getUsername());
        sale.setCancellationReason(reason.trim());
        sale.setUpdatedBy(user.getUsername());
        saleRepository.save(sale);
        audit("CREDIT_SALE_CANCELLED", "Sale", saleId, reason.trim(), user);
    }

    private CustomerRow customerRow(Customer customer) {
        List<Sale> sales = saleRepository.findByCustomerIdAndSettlementTypeOrderBySaleDateAsc(customer.getId(), SettlementType.CREDIT);
        List<CreditPayment> payments = paymentRepository.findByCustomerIdOrderByCreatedAtAsc(customer.getId());
        List<CreditCharge> charges = chargeRepository.findByCustomerIdOrderByCreatedAtAsc(customer.getId());
        BigDecimal granted = money(sales.stream().filter(s -> s.getStatus() == SaleStatus.COMPLETED).map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(charges.stream().filter(c -> c.getStatus() == CreditChargeStatus.ACTIVE).map(CreditCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        BigDecimal paid = money(payments.stream().filter(p -> p.getStatus() == CreditPaymentStatus.ACTIVE).map(CreditPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        LocalDateTime lastSale = sales.stream().map(Sale::getSaleDate).max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastPayment = payments.stream().map(CreditPayment::getUpdatedAt).max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastCharge = charges.stream().map(CreditCharge::getUpdatedAt).max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime last = java.util.stream.Stream.of(lastSale, lastPayment, lastCharge).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        return new CustomerRow(customer, money(granted.subtract(paid)), granted, paid, last);
    }

    private List<Movement> movements(List<Sale> sales, List<CreditCharge> charges, List<CreditPayment> payments) {
        List<LedgerEvent> events = new ArrayList<>();
        for (Sale sale : sales) {
            String detail = sale.getItems().stream().map(i -> i.getProductNameSnapshot() + " x " + i.getQuantity()).reduce((a, b) -> a + ", " + b).orElse("");
            events.add(new LedgerEvent(sale.getSaleDate(), "CRÉDITO", sale.getFolio(), detail, sale.getTotal(), BigDecimal.ZERO,
                    sale.getCashier() == null ? "" : sale.getCashier().getUsername(), false, sale.getId()));
            if (sale.getStatus() == SaleStatus.CANCELLED && sale.getCancelledAt() != null) {
                events.add(new LedgerEvent(sale.getCancelledAt(), "REVERSIÓN DE CRÉDITO", sale.getFolio(), sale.getCancellationReason(),
                        BigDecimal.ZERO, sale.getTotal(), sale.getCancelledBy(), true, sale.getId()));
            }
        }
        for (CreditCharge charge : charges) {
            events.add(new LedgerEvent(charge.getCreatedAt(), "CARGO DE DINERO", "CG-" + charge.getId(), charge.getConcept(),
                    charge.getAmount(), BigDecimal.ZERO, charge.getUser().getUsername(), charge.getStatus() == CreditChargeStatus.REVERSED, charge.getId()));
            if (charge.getStatus() == CreditChargeStatus.REVERSED && charge.getReversedAt() != null) {
                events.add(new LedgerEvent(charge.getReversedAt(), "REVERSIÓN DE CARGO", "CG-" + charge.getId(), charge.getReversalReason(),
                        BigDecimal.ZERO, charge.getAmount(), charge.getReversedBy(), true, charge.getId()));
            }
        }
        for (CreditPayment payment : payments) {
            events.add(new LedgerEvent(payment.getCreatedAt(), "ABONO", "AB-" + payment.getId(), payment.getNotes(),
                    BigDecimal.ZERO, payment.getAmount(), payment.getUser().getUsername(), payment.getStatus() == CreditPaymentStatus.REVERSED, payment.getId()));
            if (payment.getStatus() == CreditPaymentStatus.REVERSED && payment.getReversedAt() != null) {
                events.add(new LedgerEvent(payment.getReversedAt(), "REVERSIÓN DE ABONO", "AB-" + payment.getId(), payment.getReversalReason(),
                        payment.getAmount(), BigDecimal.ZERO, payment.getReversedBy(), true, payment.getId()));
            }
        }
        events.sort(Comparator.comparing(LedgerEvent::date));
        BigDecimal balance = BigDecimal.ZERO;
        List<Movement> result = new ArrayList<>();
        for (LedgerEvent event : events) {
            balance = money(balance.add(event.charge()).subtract(event.payment()));
            result.add(new Movement(event.date(), event.type(), event.reference(), event.detail(), event.charge(),
                    event.payment(), balance, event.username(), event.reversed(), event.entityId()));
        }
        Collections.reverse(result);
        return result;
    }

    private AppUser currentUser() {
        return userRepository.findByUsername(CurrentUser.username())
                .orElseThrow(() -> new DomainException("No se encontró el usuario actual."));
    }
    private void requireAdmin() {
        if (!CurrentUser.hasRole("ROLE_ADMIN")) throw new DomainException("Solo un administrador puede realizar esta operación.");
    }
    private void audit(String action, String entity, Long id, String details, AppUser user) {
        AuditLog log = new AuditLog();
        log.setUser(user); log.setAction(action); log.setEntityType(entity); log.setEntityId(id); log.setDetails(details);
        log.setCreatedBy(user.getUsername()); auditRepository.save(log);
    }
    private BigDecimal money(BigDecimal value) { return MoneyUtils.money(value == null ? BigDecimal.ZERO : value); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record LedgerEvent(LocalDateTime date, String type, String reference, String detail,
                               BigDecimal charge, BigDecimal payment, String username, boolean reversed, Long entityId) {}
    private record Obligation(LocalDateTime date, Sale sale, CreditCharge charge, BigDecimal pending) {}
}
