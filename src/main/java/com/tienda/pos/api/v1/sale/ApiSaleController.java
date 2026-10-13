package com.tienda.pos.api.v1.sale;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.api.v1.idempotency.ApiIdempotencyService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.sale.Sale;
import com.tienda.pos.sale.SaleRepository;
import com.tienda.pos.sale.SaleService;
import com.tienda.pos.credit.CreditPaymentApplicationRepository;
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

import static com.tienda.pos.api.v1.sale.ApiSaleModels.SaleCreateRequest;
import static com.tienda.pos.api.v1.sale.ApiSaleModels.SaleResponse;

@com.tienda.pos.common.NormalMode
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiSaleController {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;

    private final SaleRepository repository;
    private final SaleService saleService;
    private final ApiIdempotencyService idempotencyService;
    private final CreditPaymentApplicationRepository creditApplications;

    public ApiSaleController(SaleRepository repository, SaleService saleService,
                             ApiIdempotencyService idempotencyService,
                             CreditPaymentApplicationRepository creditApplications, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
        this.repository = repository;
        this.saleService = saleService;
        this.idempotencyService = idempotencyService;
        this.creditApplications = creditApplications;
    }

    @GetMapping("/sales")
    public ApiPageResponse<SaleResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<Sale> sales = CurrentUser.hasRole("ROLE_ADMIN")
                ? repository.findByTenantIdOrderBySaleDateDesc(currentTenant.id(), pageable)
                : repository.findByTenantIdAndCashierUsernameOrderBySaleDateDesc(currentTenant.id(), CurrentUser.username(), pageable);
        return ApiPageResponse.from(sales, this::response);
    }

    @GetMapping("/sales/{folio}")
    public SaleResponse detail(@PathVariable String folio) {
        return response(visibleSale(folio));
    }

    @GetMapping("/tickets/{folio}")
    public SaleResponse ticket(@PathVariable String folio) {
        return response(visibleSale(folio));
    }

    @PostMapping({"/sales", "/pos/checkout"})
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@Valid @RequestBody SaleCreateRequest request,
                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return idempotencyService.execute(CurrentUser.username(), "SALE_CREATE", idempotencyKey, request,
                SaleResponse.class, () -> {
                    String folio = saleService.checkout(request.toDomain()).folio();
                    return response(repository.findByTenantIdAndFolio(currentTenant.id(), folio)
                            .orElseThrow(() -> new ApiNotFoundException("Venta no encontrada.")));
                });
    }

    private SaleResponse response(Sale sale) {
        return SaleResponse.from(sale, creditApplications.activeAppliedToSale(currentTenant.id(), sale.getId()));
    }

    private Sale visibleSale(String folio) {
        return (CurrentUser.hasRole("ROLE_ADMIN") ? repository.findByTenantIdAndFolio(currentTenant.id(), folio)
                : repository.findByTenantIdAndFolioAndCashierUsername(currentTenant.id(), folio, CurrentUser.username()))
                .orElseThrow(() -> new ApiNotFoundException("Venta no disponible para este usuario."));
    }
}
