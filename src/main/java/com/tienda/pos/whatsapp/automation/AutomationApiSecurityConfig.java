package com.tienda.pos.whatsapp.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.common.NormalMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@NormalMode
public class AutomationApiSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain automationApiSecurity(HttpSecurity http, PlatformAutomationTokenService tokens,
                                               PlatformAutomationAuditService audits, ObjectMapper mapper) throws Exception {
        return http.securityMatcher("/api/automation/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new AutomationTokenFilter(tokens, audits, mapper), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), java.util.Map.of("error", "UNAUTHORIZED", "message", "Credencial de automatización requerida."));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), java.util.Map.of("error", "FORBIDDEN", "message", "Permiso de automatización insuficiente."));
                        }))
                .build();
    }
}
