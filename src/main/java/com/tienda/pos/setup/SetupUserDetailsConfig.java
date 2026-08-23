package com.tienda.pos.setup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@ConditionalOnProperty(name = "tienda.setup-mode", havingValue = "true")
public class SetupUserDetailsConfig {

    @Bean
    UserDetailsService setupUserDetailsService() {
        return new InMemoryUserDetailsManager(User.withUsername("setup")
                .password("{noop}setup-disabled")
                .roles("SETUP")
                .disabled(true)
                .build());
    }
}