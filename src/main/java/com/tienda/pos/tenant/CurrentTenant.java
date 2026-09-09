package com.tienda.pos.tenant;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CurrentTenant {

    private final AppUserRepository userRepository;

    public CurrentTenant(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Tenant get() {
        String username = CurrentUser.authenticatedUsername()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Inicia sesión para continuar."));
        return userRepository.findByUsernameWithTenant(username)
                .filter(AppUser::isActive)
                .map(AppUser::getTenant)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new DomainException("No se encontró un negocio activo para el usuario actual."));
    }

    @Transactional(readOnly = true)
    public Long id() {
        return get().getId();
    }
}
