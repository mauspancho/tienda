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
                        .requestMatchers("/", "/producto/**", "/catalog/**", "/css/**", "/js/**", "/vendor/**",
                                "/images/**", "/uploads/products/**", "/uploads/catalog/**", "/admin/login", "/error").permitAll()
                        .requestMatchers("/admin/users/**", "/admin/settings/**", "/admin/reports/**", "/admin/cash/**").hasRole("ADMIN")
                        .requestMatchers("/admin/products/**", "/admin/categories/**", "/admin/suppliers/**", "/admin/purchases/**",
                                "/admin/inventory/**", "/admin/expenses/**").hasRole("ADMIN")
                        .requestMatchers("/admin/pos/**", "/admin/sales/**", "/admin/tickets/**", "/admin/api/products/**").hasAnyRole("ADMIN", "CAJERO")
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(successHandler)
                        .failureUrl("/admin/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
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
