package com.tienda.pos.api.v1.supplier;

import com.tienda.pos.api.v1.common.ApiPageResponse;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.supplier.Supplier;
import com.tienda.pos.supplier.SupplierRepository;
import com.tienda.pos.supplier.SupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasRole('ADMIN')")
public class ApiSupplierController {

    private final SupplierRepository repository;
    private final SupplierService service;

    public ApiSupplierController(SupplierRepository repository, SupplierService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public ApiPageResponse<SupplierResponse> list(@RequestParam(defaultValue = "") String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                Sort.by("name").ascending());
        Page<Supplier> result = q == null || q.isBlank() ? repository.findAll(pageable)
                : repository.findByNameContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(q.trim(), q.trim(), pageable);
        return ApiPageResponse.from(result, SupplierResponse::from);
    }

    @GetMapping("/{id}")
    public SupplierResponse detail(@PathVariable Long id) {
        return SupplierResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return SupplierResponse.from(service.save(request.toEntity(null)));
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        find(id);
        return SupplierResponse.from(service.save(request.toEntity(id)));
    }

    @PatchMapping("/{id}/active")
    public SupplierResponse active(@PathVariable Long id, @RequestBody ActiveRequest request) {
        return SupplierResponse.from(service.setActive(id, request.active()));
    }

    private Supplier find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiNotFoundException("Proveedor no encontrado."));
    }

    public record SupplierRequest(String name, String companyName, String phone, String email,
                                  String address, String taxId, String notes, @NotNull Boolean active) {
        Supplier toEntity(Long id) {
            Supplier supplier = new Supplier();
            supplier.setId(id);
            supplier.setName(name);
            supplier.setCompanyName(companyName);
            supplier.setPhone(phone);
            supplier.setEmail(email);
            supplier.setAddress(address);
            supplier.setTaxId(taxId);
            supplier.setNotes(notes);
            supplier.setActive(active);
            return supplier;
        }
    }

    public record ActiveRequest(boolean active) {
    }

    public record SupplierResponse(Long id, String name, String companyName, String phone, String email,
                                   String address, String taxId, String notes, boolean active) {
        static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getCompanyName(),
                    supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.getTaxId(),
                    supplier.getNotes(), supplier.isActive());
        }
    }
}
