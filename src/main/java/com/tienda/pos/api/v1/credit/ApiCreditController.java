package com.tienda.pos.api.v1.credit;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.idempotency.ApiIdempotencyService;
import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.credit.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.tienda.pos.api.v1.credit.ApiCreditModels.*;

@RestController
@RequestMapping("/api/v1/credits")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiCreditController {
    private final CreditService service;
    private final ApiIdempotencyService idempotency;
    public ApiCreditController(CreditService service, ApiIdempotencyService idempotency) { this.service = service; this.idempotency = idempotency; }

    @GetMapping("/summary")
    public SummaryResponse summary() { return SummaryResponse.from(service.summary()); }

    @GetMapping("/customers")
    public ApiPageResponse<CustomerCreditResponse> customers(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "DEBT") String filter, @RequestParam(defaultValue = "DEBT") String sort,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<CustomerCreditResponse> all = service.customerRows(q, filter, sort).stream().map(CustomerCreditResponse::from).toList();
        int safePage = Math.max(page, 0), from = Math.min(safePage * safeSize, all.size()), to = Math.min(from + safeSize, all.size());
        return new ApiPageResponse<>(all.subList(from, to), safePage, safeSize, all.size(), all.isEmpty() ? 0 : (all.size() + safeSize - 1) / safeSize);
    }

    @GetMapping("/customers/{id}")
    public StatementResponse customer(@PathVariable Long id) { return StatementResponse.from(service.statement(id)); }

    @GetMapping("/customers/{id}/movements")
    public List<MovementView> movements(@PathVariable Long id) { return service.statement(id).movements().stream().map(MovementView::from).toList(); }

    @PostMapping("/customers/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResult payment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request,
                                 @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(CurrentUser.username(), "CREDIT_PAYMENT_CREATE", key, request, PaymentResult.class, () -> {
            CreditPayment payment = service.registerPayment(id, request.toForm());
            return new PaymentResult(payment.getId(), payment.getAmount(), payment.getMethod(), service.statement(id).balance(), payment.getCreatedAt());
        });
    }

    @PostMapping("/customers/{id}/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResult charge(@PathVariable Long id, @Valid @RequestBody ChargeRequest request,
                               @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(CurrentUser.username(), "CREDIT_CHARGE_CREATE", key, request, ChargeResult.class, () -> {
            CreditCharge charge = service.registerCharge(id, request.toForm());
            return new ChargeResult(charge.getId(), charge.getAmount(), charge.getConcept(), service.statement(id).balance(), charge.getCreatedAt());
        });
    }

    @PostMapping("/sales/{saleId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long saleId, @Valid @RequestBody ReasonRequest request) { service.cancelCredit(saleId, request.reason()); }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverse(@PathVariable Long paymentId, @Valid @RequestBody ReasonRequest request) { service.reversePayment(paymentId, request.reason()); }

    @PostMapping("/charges/{chargeId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseCharge(@PathVariable Long chargeId, @Valid @RequestBody ReasonRequest request) { service.reverseCharge(chargeId, request.reason()); }
}
