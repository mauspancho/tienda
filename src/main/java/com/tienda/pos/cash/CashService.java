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
    public void close(Long sessionId, BigDecimal countedAmount) {
        CashRegisterSession session = sessionRepository.findById(sessionId).orElseThrow();
        BigDecimal expected = movementRepository.expectedAmount(sessionId);
        session.setExpectedAmount(MoneyUtils.money(expected));
        session.setCountedAmount(MoneyUtils.money(countedAmount));
        session.setDifferenceAmount(MoneyUtils.money(countedAmount.subtract(expected)));
        session.setClosedAt(LocalDateTime.now());
        session.setOpen(false);
        sessionRepository.save(session);
    }

    public BigDecimal difference(BigDecimal expected, BigDecimal counted) {
        return MoneyUtils.money(counted.subtract(expected));
    }
}
