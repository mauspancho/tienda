package com.tienda.pos.tenant;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentTenantTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CurrentTenant currentTenant = new CurrentTenant(users);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationCannotResolveTenant() {
        assertThat(CurrentUser.authenticatedUsername()).isEmpty();
        assertThatThrownBy(currentTenant::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void anonymousTokenNeverBecomesAnAppUserOrAnAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThat(CurrentUser.authenticatedUsername()).isEmpty();
        assertThat(CurrentUser.hasRole("ROLE_ADMIN")).isFalse();
        assertThatThrownBy(currentTenant::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void unauthenticatedCredentialsCannotResolveTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("cashier", "invalid"));
        assertThatThrownBy(currentTenant::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(users);
    }

    @Test
    void authenticatedTenantComesFromStoredUserEvenWhenNamedSistema() {
        authenticate("sistema");
        Tenant tenant = new Tenant();
        tenant.setId(42L);
        AppUser user = new AppUser();
        user.setTenant(tenant);
        when(users.findByUsernameWithTenant("sistema")).thenReturn(Optional.of(user));
        assertThat(currentTenant.id()).isEqualTo(42L);
    }

    @Test
    void unknownUserHasNoTenantFallback() {
        authenticate("missing");
        when(users.findByUsernameWithTenant("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(currentTenant::get).isInstanceOf(DomainException.class);
    }

    @Test
    void userWithoutTenantIsRejected() {
        authenticate("cashier");
        when(users.findByUsernameWithTenant("cashier")).thenReturn(Optional.of(new AppUser()));
        assertThatThrownBy(currentTenant::get).isInstanceOf(DomainException.class);
    }

    @Test
    void inactiveUserOrTenantIsRejected() {
        authenticate("cashier");
        Tenant tenant = new Tenant();
        AppUser user = new AppUser();
        user.setTenant(tenant);
        when(users.findByUsernameWithTenant("cashier")).thenReturn(Optional.of(user));
        tenant.setActive(false);
        assertThatThrownBy(currentTenant::get).isInstanceOf(DomainException.class);
        tenant.setActive(true);
        user.setActive(false);
        assertThatThrownBy(currentTenant::get).isInstanceOf(DomainException.class);
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "", List.of()));
    }
}
