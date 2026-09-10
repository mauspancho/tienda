package com.tienda.pos.security;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rechecks account/tenant state for existing authenticated sessions as well as new logins. */
final class AccountAccessFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    private final SessionLogoutService logout;

    AccountAccessFilter(AppUserRepository users, SessionLogoutService logout) {
        this.users = users;
        this.logout = logout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean platform = path.equals("/platform") || path.startsWith("/platform/");
        boolean tenantArea = path.equals("/admin") || path.startsWith("/admin/");
        if ((path.equals("/") || platform || tenantArea) && !path.equals("/admin/login") && !path.equals("/admin/logout")
                && CurrentUser.authenticatedUsername().isPresent()) {
            AppUser user = users.findByUsernameWithTenant(CurrentUser.username()).orElse(null);
            boolean platformUser = user != null && user.hasRole("ROLE_PLATFORM_ADMIN");
            boolean activeTenant = user != null && user.getTenant() != null && user.getTenant().isActive();
            if (user == null || !user.isActive() || (!platformUser && !activeTenant)) {
                logout.redirect(request, response, "/admin/login?expired");
                return;
            }
            // Platform access survives tenant suspension, but operational access does not.
            if (platformUser && tenantArea && !activeTenant) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso no disponible.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
