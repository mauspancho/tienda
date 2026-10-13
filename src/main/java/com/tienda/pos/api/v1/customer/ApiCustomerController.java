package com.tienda.pos.api.v1.customer;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.customer.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.tienda.pos.api.v1.customer.ApiCustomerModels.*;

@com.tienda.pos.common.NormalMode
@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
public class ApiCustomerController {
    private final CustomerService service;
    public ApiCustomerController(CustomerService service) { this.service = service; }

    @GetMapping
    public ApiPageResponse<CustomerResponse> list(@RequestParam(defaultValue = "") String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        List<CustomerResponse> all = service.search(q).stream().map(CustomerResponse::from).toList();
        int from = Math.min(Math.max(page, 0) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = all.isEmpty() ? 0 : (all.size() + safeSize - 1) / safeSize;
        return new ApiPageResponse<>(all.subList(from, to), Math.max(page, 0), safeSize, all.size(), totalPages);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.create(request.toForm()));
    }

    @GetMapping("/{id}")
    public CustomerResponse detail(@PathVariable Long id) { return CustomerResponse.from(service.get(id)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.update(id, request.toForm()));
    }
}
