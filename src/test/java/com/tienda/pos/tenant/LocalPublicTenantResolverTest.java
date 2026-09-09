package com.tienda.pos.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalPublicTenantResolverTest {

    private final TenantRepository tenants = mock(TenantRepository.class);

    @Test
    void unconfiguredLocalCatalogUsesTheOnlyActiveTenant() {
        Tenant tenant = new Tenant();
        when(tenants.findTop2ByActiveTrueOrderByIdAsc()).thenReturn(List.of(tenant));
        assertThat(new LocalPublicTenantResolver(tenants, "").resolve()).isSameAs(tenant);
    }

    @Test
    void missingOrAmbiguousLocalTenantIsUnavailable() {
        LocalPublicTenantResolver resolver = new LocalPublicTenantResolver(tenants, "");
        when(tenants.findTop2ByActiveTrueOrderByIdAsc()).thenReturn(List.of());
        assertUnavailable(resolver);
        when(tenants.findTop2ByActiveTrueOrderByIdAsc()).thenReturn(List.of(new Tenant(), new Tenant()));
        assertUnavailable(resolver);
    }

    @Test
    void configuredTenantDoesNotRequireAnAuthenticatedUser() {
        Tenant tenant = new Tenant();
        when(tenants.findByCode("public")).thenReturn(Optional.of(tenant));
        assertThat(new LocalPublicTenantResolver(tenants, " public ").resolve()).isSameAs(tenant);
        verify(tenants, never()).findTop2ByActiveTrueOrderByIdAsc();
    }

    @Test
    void invalidOrInactiveConfigurationNeverFallsBack() {
        LocalPublicTenantResolver resolver = new LocalPublicTenantResolver(tenants, "public");
        when(tenants.findByCode("public")).thenReturn(Optional.empty());
        assertUnavailable(resolver);
        Tenant inactive = new Tenant();
        inactive.setActive(false);
        when(tenants.findByCode("public")).thenReturn(Optional.of(inactive));
        assertUnavailable(resolver);
        verify(tenants, never()).findTop2ByActiveTrueOrderByIdAsc();
    }

    private void assertUnavailable(LocalPublicTenantResolver resolver) {
        assertThatThrownBy(resolver::resolve).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
