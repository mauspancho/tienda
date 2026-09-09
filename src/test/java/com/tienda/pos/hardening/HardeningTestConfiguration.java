package com.tienda.pos.hardening;

import com.tienda.pos.catalog.CatalogController;
import com.tienda.pos.catalog.CatalogService;
import com.tienda.pos.common.DisplayFormat;
import com.tienda.pos.commercial.StoreContextService;
import com.tienda.pos.exception.GlobalExceptionHandler;
import com.tienda.pos.inventory.InventoryService;
import com.tienda.pos.sale.SaleService;
import com.tienda.pos.security.LoginSuccessService;
import com.tienda.pos.security.DatabaseUserDetailsService;
import com.tienda.pos.security.SecurityConfig;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.LocalPublicTenantResolver;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("com.tienda.pos")
@EnableJpaRepositories("com.tienda.pos")
@Import({SecurityConfig.class, LoginSuccessService.class, DatabaseUserDetailsService.class, CatalogController.class,
        CatalogService.class, LocalPublicTenantResolver.class, CurrentTenant.class,
        DisplayFormat.class, GlobalExceptionHandler.class, SaleService.class,
        InventoryService.class, StoreContextService.class})
public class HardeningTestConfiguration {
}
