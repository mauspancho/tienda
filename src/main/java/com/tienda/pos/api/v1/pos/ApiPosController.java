package com.tienda.pos.api.v1.pos;

import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.cash.CashRegisterSessionRepository;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pos")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiPosController {

    private final AppUserRepository userRepository;
    private final CashRegisterSessionRepository sessionRepository;

    public ApiPosController(AppUserRepository userRepository, CashRegisterSessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public PosState state() {
        AppUser user = userRepository.findByUsername(CurrentUser.username()).orElseThrow();
        CashRegisterSession session = sessionRepository.findByCashierAndOpenTrue(user).orElse(null);
        return new PosState(session != null, session == null ? null : CashSession.from(session),
                List.of(PaymentMethod.values()));
    }

    public record PosState(boolean cashOpen, CashSession cashSession, List<PaymentMethod> paymentMethods) {
    }

    public record CashSession(Long id, LocalDateTime openedAt, BigDecimal openingAmount) {
        static CashSession from(CashRegisterSession session) {
            return new CashSession(session.getId(), session.getOpenedAt(), session.getOpeningAmount());
        }
    }
}
