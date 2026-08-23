package com.tienda.pos.setup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "tienda.setup-mode", havingValue = "true")
public class SetupSecurityConfig {

    @Bean
    SecurityFilterChain setupSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/setup/**", "/css/**", "/js/**").permitAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/setup/test", "/setup/install"))
                .build();
    }
}
