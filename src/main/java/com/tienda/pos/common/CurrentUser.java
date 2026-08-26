package com.tienda.pos.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static String username() {
        Authentication authentication = authentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "sistema";
        }
        return authentication.getName();
    }

    public static boolean hasRole(String role) {
        Authentication authentication = authentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
