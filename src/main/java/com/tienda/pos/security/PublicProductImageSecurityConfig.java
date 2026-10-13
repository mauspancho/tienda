package com.tienda.pos.security;

import com.tienda.pos.common.NormalMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@NormalMode
public class PublicProductImageSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain publicProductImages(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/uploads/products/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
