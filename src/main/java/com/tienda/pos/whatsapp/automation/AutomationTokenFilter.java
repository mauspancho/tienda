package com.tienda.pos.whatsapp.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AutomationTokenFilter extends OncePerRequestFilter {
    private final PlatformAutomationTokenService tokens;
    private final PlatformAutomationAuditService audits;
    private final ObjectMapper mapper;
    public AutomationTokenFilter(PlatformAutomationTokenService tokens, PlatformAutomationAuditService audits, ObjectMapper mapper) {
        this.tokens = tokens; this.audits = audits; this.mapper = mapper;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AutomationPrincipal principal = null;
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                principal = tokens.authenticate(header.substring(7).trim());
                var authorities = principal.scopes().stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.name())).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            }
            chain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext(); response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), java.util.Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
        } finally {
            if (principal != null) {
                try {
                    audits.record(principal.tokenId(), request.getMethod() + " " + request.getRequestURI(), tenantCode(request),
                            response.getStatus(), request.getRemoteAddr());
                } catch (RuntimeException ignored) { }
            }
        }
    }
    private String tenantCode(HttpServletRequest request) {
        String[] parts = request.getRequestURI().split("/");
        for (int i = 0; i + 1 < parts.length; i++) if ("stores".equals(parts[i])) return parts[i + 1];
        return null;
    }
}
