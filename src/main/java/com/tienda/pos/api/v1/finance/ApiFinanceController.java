package com.tienda.pos.api.v1.finance;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.finance.CapitalMovement;
import com.tienda.pos.finance.CapitalMovementForm;
import com.tienda.pos.finance.CapitalMovementRepository;
import com.tienda.pos.finance.CapitalMovementType;
import com.tienda.pos.finance.DailyFinanceSummary;
import com.tienda.pos.finance.FinancePeriodSummary;
import com.tienda.pos.finance.FinanceRange;
import com.tienda.pos.finance.FinanceService;
import com.tienda.pos.finance.FinanceSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finances")
@PreAuthorize("hasRole('ADMIN')")
public class ApiFinanceController {

    private final FinanceService financeService;
    private final CapitalMovementRepository capitalRepository;

    public ApiFinanceController(FinanceService financeService, CapitalMovementRepository capitalRepository) {
        this.financeService = financeService;
        this.capitalRepository = capitalRepository;
    }

    @GetMapping
    public FinanceSummary summary(@RequestParam(defaultValue = "LAST_30_DAYS") String period,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to,
                                  @RequestParam(defaultValue = "profit") String productSort) {
        return financeService.summary(period, from, to, productSort);
    }

    @GetMapping("/daily")
    public List<DailyFinanceSummary> daily(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        FinanceRange range = range(from, to);
        return financeService.daily(range.from(), range.to());
    }

    @GetMapping("/daily/detail")
    public FinancePeriodSummary detail(@RequestParam LocalDate date) {
        return financeService.detail(date);
    }

    @GetMapping(value = "/daily.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> dailyCsv(@RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        FinanceRange range = range(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finanzas-diarias.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(financeService.dailyCsv(range.from(), range.to()));
    }

    @GetMapping("/capital")
    public ApiPageResponse<CapitalResponse> capital(@RequestParam(required = false) CapitalMovementType type,
                                                    @RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        FinanceRange range = range(from, to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<CapitalMovement> result = type == null
                ? capitalRepository.findByMovementDateBetweenOrderByMovementDateDesc(range.from(), range.to(), pageable)
                : capitalRepository.findByTypeAndMovementDateBetweenOrderByMovementDateDesc(type, range.from(), range.to(), pageable);
        return ApiPageResponse.from(result, CapitalResponse::from);
    }

    @PostMapping("/capital")
    @ResponseStatus(HttpStatus.CREATED)
    public CapitalResponse createCapital(@Valid @RequestBody CapitalRequest request) {
        CapitalMovementForm form = new CapitalMovementForm();
        form.setMovementDate(request.movementDate());
        form.setType(request.type());
        form.setAmount(request.amount());
        form.setDescription(request.description());
        return CapitalResponse.from(financeService.registerCapitalMovement(form));
    }

    private FinanceRange range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        return financeService.range("CUSTOM", from == null ? today.minusDays(29) : from, to == null ? today : to);
    }

    public record CapitalRequest(@NotNull LocalDate movementDate, @NotNull CapitalMovementType type,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount, String description) {
    }

    public record CapitalResponse(Long id, LocalDate movementDate, CapitalMovementType type,
                                  BigDecimal amount, String description, Long purchaseId) {
        static CapitalResponse from(CapitalMovement movement) {
            return new CapitalResponse(movement.getId(), movement.getMovementDate(), movement.getType(),
                    movement.getAmount(), movement.getDescription(), movement.getPurchaseId());
        }
    }
}
