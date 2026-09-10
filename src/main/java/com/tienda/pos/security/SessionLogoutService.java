package com.tienda.pos.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.io.IOException;

public final class SessionLogoutService {
    private final SessionRegistry registry;
    private final CompositeLogoutHandler handler = new CompositeLogoutHandler(
            new CookieClearingLogoutHandler("JSESSIONID"), new SecurityContextLogoutHandler());

    public SessionLogoutService(SessionRegistry registry) {
        this.registry = registry;
    }

    public void redirect(HttpServletRequest request, HttpServletResponse response, String destination) throws IOException {
        var session = request.getSession(false);
        if (session != null) registry.removeSessionInformation(session.getId());
        handler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        new DefaultRedirectStrategy().sendRedirect(request, response, destination);
    }
}
