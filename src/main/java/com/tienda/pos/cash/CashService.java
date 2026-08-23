package com.tienda.pos.cash;

import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
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

    public CashService(CashRegisterSessionRepository sessionRepository, CashMovementRepository movementRepository,
                       AppUserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void open(String username, BigDecimal openingAmount) {
        AppUser cashier = userRepository.findByUsername(username).orElseThrow();
        if (sessionRepository.findByCashierAndOpenTrue(cashier).isPresent()) {
            throw new DomainException("Este cajero ya tiene una caja abierta.");
        }
        CashRegisterSession session = new CashRegisterSession();
        session.setCashier(cashier);
        session.setOpeningAmount(MoneyUtils.money(openingAmount));
        sessionRepository.save(session);
        CashMovement movement = new CashMovement();
        movement.setCashRegisterSession(session);
        movement.setType(CashMovementType.OPENING);
        movement.setAmount(session.getOpeningAmount());
        movement.setUser(cashier);
        movementRepository.save(movement);
    }

    @Transactional
    public void close(String username, Long sessionId, BigDecimal countedAmount) {
        AppUser closer = userRepository.findByUsername(username).orElseThrow();
        CashRegisterSession session = sessionRepository.findById(sessionId).orElseThrow();
        ensureCanClose(closer, session);
        BigDecimal expected = movementRepository.expectedAmount(sessionId);
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
