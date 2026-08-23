package com.tienda.pos.cash;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.role.Role;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashServiceTest {

    @Test
    void calculatesCashDifference() {
        CashService service = new CashService(null, null, null);

        assertThat(service.difference(new BigDecimal("3300"), new BigDecimal("3280")))
                .isEqualByComparingTo(new BigDecimal("-20.00"));
    }

    @Test
    void cashierCannotCloseAnotherUsersCashSession() {
        CashRegisterSessionRepository sessionRepository = mock(CashRegisterSessionRepository.class);
        CashMovementRepository movementRepository = mock(CashMovementRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CashService service = new CashService(sessionRepository, movementRepository, userRepository);
        AppUser closer = user(1L, "cajero", "ROLE_CAJERO");
        CashRegisterSession session = cashSession(2L, user(2L, "admin", "ROLE_ADMIN"));

        when(userRepository.findByUsername("cajero")).thenReturn(Optional.of(closer));
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.close("cajero", 2L, new BigDecimal("100")))
                .isInstanceOf(DomainException.class)
                .hasMessage("Solo un administrador puede cerrar la caja de otro usuario.");
        assertThat(session.isOpen()).isTrue();
        verify(sessionRepository, never()).save(session);
    }

    @Test
    void adminCanCloseAnotherUsersCashSession() {
        CashRegisterSessionRepository sessionRepository = mock(CashRegisterSessionRepository.class);
        CashMovementRepository movementRepository = mock(CashMovementRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CashService service = new CashService(sessionRepository, movementRepository, userRepository);
        AppUser admin = user(1L, "admin", "ROLE_ADMIN");
        CashRegisterSession session = cashSession(2L, user(2L, "cajero", "ROLE_CAJERO"));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(movementRepository.expectedAmount(2L)).thenReturn(new BigDecimal("90"));

        service.close("admin", 2L, new BigDecimal("100"));

        assertThat(session.isOpen()).isFalse();
        assertThat(session.getExpectedAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(session.getCountedAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(session.getDifferenceAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        verify(sessionRepository).save(session);
    }

    @Test
    void cashierCanCloseOwnCashSession() {
        CashRegisterSessionRepository sessionRepository = mock(CashRegisterSessionRepository.class);
        CashMovementRepository movementRepository = mock(CashMovementRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        CashService service = new CashService(sessionRepository, movementRepository, userRepository);
        AppUser cashier = user(1L, "cajero", "ROLE_CAJERO");
        CashRegisterSession session = cashSession(2L, cashier);

        when(userRepository.findByUsername("cajero")).thenReturn(Optional.of(cashier));
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(movementRepository.expectedAmount(2L)).thenReturn(new BigDecimal("100"));

        service.close("cajero", 2L, new BigDecimal("100"));

        assertThat(session.isOpen()).isFalse();
        verify(sessionRepository).save(session);
    }

    private AppUser user(Long id, String username, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.getRoles().add(new Role(role));
        return user;
    }

    private CashRegisterSession cashSession(Long id, AppUser cashier) {
        CashRegisterSession session = new CashRegisterSession();
        session.setId(id);
        session.setCashier(cashier);
        session.setOpen(true);
        return session;
    }
}
