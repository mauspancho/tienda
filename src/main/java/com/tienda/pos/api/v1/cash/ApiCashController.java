package com.tienda.pos.api.v1.cash;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.cash.CashMovement;
import com.tienda.pos.cash.CashMovementRepository;
import com.tienda.pos.cash.CashMovementType;
import com.tienda.pos.cash.CashRegisterSession;
import com.tienda.pos.cash.CashRegisterSessionRepository;
import com.tienda.pos.cash.CashService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cash")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiCashController {

    private final CashRegisterSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;
    private final AppUserRepository userRepository;
    private final CashService cashService;

    public ApiCashController(CashRegisterSessionRepository sessionRepository,
                             CashMovementRepository movementRepository,
                             AppUserRepository userRepository, CashService cashService) {
        this.sessionRepository = sessionRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.cashService = cashService;
    }

    @GetMapping("/current")
    public CurrentCashResponse current() {
        AppUser user = userRepository.findByUsername(CurrentUser.username())
                .orElseThrow(() -> new ApiNotFoundException("Usuario no encontrado."));
        CashRegisterSession session = sessionRepository.findDetailedByCashierAndOpenTrue(user).orElse(null);
        return new CurrentCashResponse(session != null, session == null ? null : SessionResponse.from(session));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiPageResponse<SessionResponse> sessions(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        Page<CashRegisterSession> sessions = sessionRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100))));
        return ApiPageResponse.from(sessions, SessionResponse::from);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CashSessionDetail session(@PathVariable Long id) {
        CashRegisterSession session = find(id);
        List<MovementResponse> movements = movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(id)
                .stream().map(MovementResponse::from).toList();
        return new CashSessionDetail(SessionResponse.from(session), movements);
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse open(@Valid @RequestBody OpenRequest request) {
        cashService.open(CurrentUser.username(), request.openingAmount());
        AppUser user = userRepository.findByUsername(CurrentUser.username()).orElseThrow();
        return SessionResponse.from(sessionRepository.findDetailedByCashierAndOpenTrue(user).orElseThrow());
    }

    @PostMapping("/{id}/close")
    public SessionResponse close(@PathVariable Long id, @Valid @RequestBody CloseRequest request) {
        cashService.close(CurrentUser.username(), id, request.countedAmount());
        return SessionResponse.from(find(id));
    }

    private CashRegisterSession find(Long id) {
        return sessionRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiNotFoundException("Sesión de caja no encontrada."));
    }

    public record OpenRequest(@NotNull @DecimalMin("0.00") BigDecimal openingAmount) {
    }

    public record CloseRequest(@NotNull @DecimalMin("0.00") BigDecimal countedAmount) {
    }

    public record CurrentCashResponse(boolean open, SessionResponse session) {
    }

    public record SessionResponse(Long id, Long cashierId, String cashierName, LocalDateTime openedAt,
                                  LocalDateTime closedAt, BigDecimal openingAmount, BigDecimal expectedAmount,
                                  BigDecimal countedAmount, BigDecimal differenceAmount, boolean open) {
        static SessionResponse from(CashRegisterSession session) {
            AppUser cashier = session.getCashier();
            return new SessionResponse(session.getId(), cashier == null ? null : cashier.getId(),
                    cashier == null ? null : cashier.fullName(), session.getOpenedAt(), session.getClosedAt(),
                    session.getOpeningAmount(), session.getExpectedAmount(), session.getCountedAmount(),
                    session.getDifferenceAmount(), session.isOpen());
        }
    }

    public record MovementResponse(Long id, CashMovementType type, BigDecimal amount,
                                   String referenceType, Long referenceId, String notes,
                                   Long userId, String username, LocalDateTime createdAt) {
        static MovementResponse from(CashMovement movement) {
            AppUser user = movement.getUser();
            return new MovementResponse(movement.getId(), movement.getType(), movement.getAmount(),
                    movement.getReferenceType(), movement.getReferenceId(), movement.getNotes(),
                    user == null ? null : user.getId(), user == null ? null : user.getUsername(),
                    movement.getCreatedAt());
        }
    }

    public record CashSessionDetail(SessionResponse session, List<MovementResponse> movements) {
    }
}
