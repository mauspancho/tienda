package com.tienda.pos.platform;

import com.tienda.pos.role.Role;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.EntityManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Local-only visual QA fixture. Not packaged in the production JAR. */
public class PlatformPreviewServer {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlatformTestConfiguration.class);
        app.addInitializers(context -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "tienda.setup-mode=false", "spring.autoconfigure.exclude=", "spring.flyway.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:platform-preview;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false", "tienda.catalog.tenant-code=", "server.address=127.0.0.1", "server.port=0"));
        var context = app.run("--spring.config.location=classpath:/application.yml");
        var em = context.getBean(EntityManager.class);
        String hash = context.getBean(PasswordEncoder.class).encode("Preview-only-493!");
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            Role admin = new Role(); admin.setName("ROLE_ADMIN"); em.persist(admin);
            Role platform = new Role(); platform.setName("ROLE_PLATFORM_ADMIN"); em.persist(platform);
            Tenant tenant = new Tenant(); tenant.setCode("platform"); tenant.setName("Plataforma"); em.persist(tenant);
            AppUser user = new AppUser(); user.setTenant(tenant); user.setUsername("preview"); user.setFirstName("QA");
            user.setLastName("Local"); user.setPasswordHash(hash); user.getRoles().add(admin); user.getRoles().add(platform); em.persist(user);
        });
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "preview", "", AuthorityUtils.createAuthorityList("ROLE_PLATFORM_ADMIN")));
        var service = context.getBean(TenantProvisioningService.class);
        service.create(PlatformIntegrationTest.form("tienda-centro"));
        Long suspended = service.create(PlatformIntegrationTest.form("tienda-norte"));
        context.getBean(PlatformTenantService.class).toggle(suspended);
        SecurityContextHolder.clearContext();
        System.out.println("PLATFORM_PREVIEW_URL=http://127.0.0.1:" +
                ((ServletWebServerApplicationContext) context).getWebServer().getPort());
    }
}
