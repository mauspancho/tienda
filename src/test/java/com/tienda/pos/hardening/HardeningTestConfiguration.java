package com.tienda.pos.hardening;

import com.tienda.pos.auth.RootController;
import com.tienda.pos.security.SessionRevocationService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("com.tienda.pos")
@EnableJpaRepositories("com.tienda.pos")
@Import({SecurityConfig.class, LoginSuccessService.class, DatabaseUserDetailsService.class, RootController.class,
        SessionRevocationService.class,
        CatalogService.class, LocalPublicTenantResolver.class, CurrentTenant.class,
        DisplayFormat.class, GlobalExceptionHandler.class, SaleService.class,
        InventoryService.class, StoreContextService.class})
public class HardeningTestConfiguration {
}
