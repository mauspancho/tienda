package com.tienda.pos.cash;

import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@NormalMode
public class CashService {

    private final CashRegisterSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;
    private final AppUserRepository userRepository;
    private final StoreContextService storeContextService;
    private final CurrentTenant currentTenant;

    public CashService(CashRegisterSessionRepository sessionRepository, CashMovementRepository movementRepository,
                       AppUserRepository userRepository, StoreContextService storeContextService,
                       CurrentTenant currentTenant) {
        this.sessionRepository = sessionRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.storeContextService = storeContextService;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public void open(String username, BigDecimal openingAmount) {
        Tenant tenant = currentTenant.get();
        AppUser cashier = userRepository.findByUsernameWithTenant(username)
                .filter(user -> user.getTenant() != null && user.getTenant().getId().equals(tenant.getId()))
                .orElseThrow();
        if (sessionRepository.findByTenantIdAndCashierAndOpenTrue(tenant.getId(), cashier).isPresent()) {
            throw new DomainException("Este cajero ya tiene una caja abierta.");
        }
        CashRegister cashRegister = storeContextService.defaultCashRegister();
        CashRegisterSession session = new CashRegisterSession();
        session.setTenant(tenant);
        session.setCashier(cashier);
        session.setBranch(cashRegister.getBranch());
        session.setCashRegister(cashRegister);
        session.setOpeningAmount(MoneyUtils.money(openingAmount));
        sessionRepository.save(session);
        CashMovement movement = new CashMovement();
        movement.setTenant(tenant);
        movement.setCashRegisterSession(session);
        movement.setType(CashMovementType.OPENING);
        movement.setAmount(session.getOpeningAmount());
        movement.setUser(cashier);
        movementRepository.save(movement);
    }

    @Transactional
    public void close(String username, Long sessionId, BigDecimal countedAmount) {
        Tenant tenant = currentTenant.get();
        AppUser closer = userRepository.findByUsernameWithTenant(username)
                .filter(user -> user.getTenant() != null && user.getTenant().getId().equals(tenant.getId()))
                .orElseThrow();
        CashRegisterSession session = sessionRepository.findByIdAndTenantId(sessionId, tenant.getId())
                .orElseThrow(() -> new DomainException("Caja no encontrada."));
        ensureCanClose(closer, session);
        BigDecimal expected = movementRepository.expectedAmount(tenant.getId(), sessionId);
        session.setExpectedAmount(MoneyUtils.money(expected));
        session.setCountedAmount(MoneyUtils.money(countedAmount));
        session.setDifferenceAmount(MoneyUtils.money(countedAmount.subtract(expected)));
        session.setClosedAt(LocalDateTime.now());
        session.setOpen(false);
        sessionRepository.save(session);
    }

    private void ensureCanClose(AppUser closer, CashRegisterSession session) {
        if (closer.hasRole("ROLE_ADMIN")) {
            return;
        }
        AppUser sessionCashier = session.getCashier();
        if (sessionCashier == null || !Objects.equals(sessionCashier.getId(), closer.getId())) {
            throw new DomainException("Solo un administrador puede cerrar la caja de otro usuario.");
        }
    }

    public BigDecimal difference(BigDecimal expected, BigDecimal counted) {
        return MoneyUtils.money(counted.subtract(expected));
    }
}