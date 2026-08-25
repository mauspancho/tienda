package com.tienda.pos.security;

import com.tienda.pos.common.NormalMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@NormalMode
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationSuccessHandler successHandler(LoginSuccessService loginSuccessService) {
        return loginSuccessService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/login", "/error").permitAll()
                        .requestMatchers("/users/**", "/settings/**", "/reports/**", "/cash/**").hasRole("ADMIN")
                        .requestMatchers("/products/**", "/categories/**", "/suppliers/**", "/purchases/**",
                                "/inventory/**", "/expenses/**").hasRole("ADMIN")
                        .requestMatchers("/pos/**", "/sales/**", "/tickets/**", "/api/products/**").hasAnyRole("ADMIN", "CAJERO")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .maximumSessions(1))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; object-src 'none'; frame-ancestors 'self'")))
                .build();
    }
}
