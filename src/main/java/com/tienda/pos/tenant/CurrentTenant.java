package com.tienda.pos.tenant;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CurrentTenant {

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;

    public CurrentTenant(AppUserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Tenant get() {
        String username = CurrentUser.username();
        if (!"sistema".equals(username)) {
            return userRepository.findByUsernameWithTenant(username)
                    .map(user -> user.getTenant())
                    .filter(tenant -> tenant != null && tenant.isActive())
                    .orElseThrow(() -> new DomainException("No se encontró el negocio del usuario actual."));
        }
        return tenantRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new DomainException("No existe un negocio activo configurado."));
    }

    @Transactional(readOnly = true)
    public Long id() {
        return get().getId();
    }
}