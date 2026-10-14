package com.tienda.pos.whatsapp.automation;

import java.util.Set;

public record AutomationPrincipal(Long tokenId, Set<AutomationScope> scopes) {
    public boolean permits(AutomationScope scope) { return scopes.contains(scope); }
}
