package com.tienda.pos.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class LoginDestination {
    private LoginDestination() {}

    public static String forAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return "/admin/login";
        for (String role : new String[]{"PLATFORM_ADMIN", "ADMIN", "CAJERO"}) {
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role))) {
                return switch (role) {
                    case "PLATFORM_ADMIN" -> "/platform/tenants";
                    case "ADMIN" -> "/admin";
                    default -> "/admin/pos";
                };
            }
        }
        return null;
    }
}
