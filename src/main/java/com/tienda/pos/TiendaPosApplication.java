package com.tienda.pos;

import com.tienda.pos.externalproduct.ExternalProductsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties(ExternalProductsProperties.class)
public class TiendaPosApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiendaPosApplication.class, args);
    }
}
