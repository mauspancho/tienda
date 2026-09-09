package com.tienda.pos.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class LocalPublicTenantResolver implements PublicTenantResolver {

    private final TenantRepository tenantRepository;
    private final String tenantCode;

    public LocalPublicTenantResolver(TenantRepository tenantRepository,
                                    @Value("${tienda.catalog.tenant-code:}") String tenantCode) {
        this.tenantRepository = tenantRepository;
        this.tenantCode = tenantCode.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant resolve() {
        if (!tenantCode.isEmpty()) {
            return tenantRepository.findByCode(tenantCode)
                    .filter(Tenant::isActive)
                    .orElseThrow(this::unavailable);
        }
        List<Tenant> candidates = tenantRepository.findTop2ByActiveTrueOrderByIdAsc();
        if (candidates.size() != 1) {
            throw unavailable();
        }
        return candidates.getFirst();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El catalogo no esta disponible.");
    }
}
