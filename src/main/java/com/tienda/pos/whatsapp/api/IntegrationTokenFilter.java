package com.tienda.pos.whatsapp.api;

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

public class IntegrationTokenFilter extends OncePerRequestFilter {
    private final IntegrationTokenService service;
    private final ObjectMapper mapper;
    public IntegrationTokenFilter(IntegrationTokenService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                IntegrationPrincipal principal = service.authenticate(header.substring(7).trim());
                var authorities = principal.scopes().stream().map(s -> new SimpleGrantedAuthority("SCOPE_WHATSAPP_" + s.name())).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
            }
            chain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext(); response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), java.util.Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
        }
    }
}
