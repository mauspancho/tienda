package com.tienda.pos.security;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@NormalMode
public class SecurityConfig {

    @Bean
    SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    SessionLogoutService sessionLogoutService(SessionRegistry registry) { return new SessionLogoutService(registry); }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationSuccessHandler successHandler(LoginSuccessService loginSuccessService) {
        return loginSuccessService;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler,
                                            AppUserRepository userRepository, SessionRegistry registry,
                                            SessionLogoutService logoutService) throws Exception {
        RequestMatcher retiredStorefront = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return path.equals("/producto") || path.startsWith("/producto/")
                    || path.equals("/catalog") || path.startsWith("/catalog/");
        };
        var loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/admin/login");
        var accessDenied = new AccessDeniedHandlerImpl();
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(retiredStorefront).denyAll()
                        .requestMatchers("/platform", "/platform/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/", "/css/**", "/js/**", "/vendor/**",
                                "/images/**", "/uploads/products/**", "/uploads/catalog/**", "/admin/login", "/error").permitAll()
                        .requestMatchers("/admin/users/**", "/admin/settings/**", "/admin/reports/**", "/admin/cash/**", "/admin/finances", "/admin/finances/**").hasRole("ADMIN")
                        .requestMatchers("/admin/products/**", "/admin/categories/**", "/admin/suppliers/**", "/admin/purchases/**",
                                "/admin/inventory/**", "/admin/expenses/**").hasRole("ADMIN")
                        .requestMatchers("/admin/pos/**", "/admin/sales/**", "/admin/tickets/**", "/admin/api/products/**").hasAnyRole("ADMIN", "CAJERO")
                        .requestMatchers("/admin/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            if (retiredStorefront.matches(request)) response.sendError(404);
                            else loginEntryPoint.commence(request, response, ex);
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            if (retiredStorefront.matches(request)) response.sendError(404);
                            else accessDenied.handle(request, response, ex);
                        }))
                .addFilterBefore(new AccountAccessFilter(userRepository, logoutService), CsrfFilter.class)
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(successHandler)
                        .failureHandler(new AccountAuthenticationFailureHandler())
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
                        .maximumSessions(1)
                        .sessionRegistry(registry)
                        .expiredSessionStrategy(event -> logoutService.redirect(event.getRequest(), event.getResponse(),
                                "/admin/login?sessionExpired")))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; object-src 'none'; frame-ancestors 'self'")))
                .build();
    }
}
