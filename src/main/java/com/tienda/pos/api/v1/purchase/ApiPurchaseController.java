package com.tienda.pos.api.v1.purchase;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.api.v1.idempotency.ApiIdempotencyService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.purchase.Purchase;
import com.tienda.pos.purchase.PurchaseFundingSource;
import com.tienda.pos.purchase.PurchaseRepository;
import com.tienda.pos.purchase.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.tienda.pos.api.v1.purchase.ApiPurchaseModels.PurchaseCreateRequest;
import static com.tienda.pos.api.v1.purchase.ApiPurchaseModels.PurchaseResponse;
import static com.tienda.pos.api.v1.purchase.ApiPurchaseModels.PurchaseSummary;

@RestController
@RequestMapping("/api/v1/purchases")
@PreAuthorize("hasRole('ADMIN')")
public class ApiPurchaseController {

    private final PurchaseRepository repository;
    private final PurchaseService service;
    private final ApiIdempotencyService idempotencyService;

    public ApiPurchaseController(PurchaseRepository repository, PurchaseService service,
                                 ApiIdempotencyService idempotencyService) {
        this.repository = repository;
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public ApiPageResponse<PurchaseSummary> list(@RequestParam(required = false) PurchaseFundingSource fundingSource,
                                                 @RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            LocalDate previous = start;
            start = end;
            end = previous;
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<Purchase> purchases = fundingSource == null
                ? repository.findByPurchaseDateBetweenOrderByPurchaseDateDesc(start, end, pageable)
                : repository.findByFundingSourceAndPurchaseDateBetweenOrderByPurchaseDateDesc(
                        fundingSource, start, end, pageable);
        return ApiPageResponse.from(purchases, PurchaseSummary::from);
    }

    @GetMapping("/{id}")
    public PurchaseResponse detail(@PathVariable Long id) {
        return PurchaseResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse create(@Valid @RequestBody PurchaseCreateRequest request,
                                   @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return idempotencyService.execute(CurrentUser.username(), "PURCHASE_CREATE", idempotencyKey, request,
                PurchaseResponse.class, () -> {
                    Purchase saved = service.register(request.toDomain());
                    return PurchaseResponse.from(find(saved.getId()));
                });
    }

    private Purchase find(Long id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> new ApiNotFoundException("Compra no encontrada."));
    }
}
