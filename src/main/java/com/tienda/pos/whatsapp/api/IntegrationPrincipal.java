package com.tienda.pos.whatsapp.api;

import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.IntegrationScope;
import java.util.Set;

public record IntegrationPrincipal(Long tokenId, Tenant tenant, Set<IntegrationScope> scopes) {
    public boolean permits(IntegrationScope scope) { return scopes.contains(scope); }
}
