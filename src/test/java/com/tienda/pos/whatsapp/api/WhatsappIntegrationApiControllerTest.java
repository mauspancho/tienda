package com.tienda.pos.whatsapp.api;

import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.IntegrationScope;
import com.tienda.pos.whatsapp.WhatsappIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WhatsappIntegrationApiControllerTest {
    @Test void tokenCannotUseAnotherTenantCode() {
        Tenant tenant = new Tenant(); tenant.setId(1L); tenant.setCode("store-one"); tenant.setName("Store one");
        WhatsappIntegrationService service = mock(WhatsappIntegrationService.class);
        WhatsappIntegrationApiController controller = new WhatsappIntegrationApiController(service, mock(IntegrationRateLimiter.class), mock(IntegrationIdempotencyService.class));
        IntegrationPrincipal principal = new IntegrationPrincipal(10L, tenant, Set.of(IntegrationScope.STATUS));
        assertThatThrownBy(() -> controller.status("store-two", principal)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test void missingScopeCannotSend() {
        Tenant tenant = new Tenant(); tenant.setId(1L); tenant.setCode("store-one"); tenant.setName("Store one");
        WhatsappIntegrationService service = mock(WhatsappIntegrationService.class);
        WhatsappIntegrationApiController controller = new WhatsappIntegrationApiController(service, mock(IntegrationRateLimiter.class), mock(IntegrationIdempotencyService.class));
        IntegrationPrincipal principal = new IntegrationPrincipal(10L, tenant, Set.of(IntegrationScope.STATUS));
        assertThatThrownBy(() -> controller.send("store-one", principal, new WhatsappSendRequest("5215512345678", false, "hola"), null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
}
