package com.tienda.pos.whatsapp.api;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.whatsapp.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@NormalMode
@RequestMapping("/api/integrations/v1/{tenantCode}/whatsapp")
public class WhatsappIntegrationApiController {
    private final WhatsappIntegrationService service;
    private final IntegrationRateLimiter rateLimiter;
    private final IntegrationIdempotencyService idempotency;
    public WhatsappIntegrationApiController(WhatsappIntegrationService service, IntegrationRateLimiter rateLimiter,
                                            IntegrationIdempotencyService idempotency) { this.service = service; this.rateLimiter = rateLimiter; this.idempotency = idempotency; }

    @GetMapping("/status")
    public Map<String,Object> status(@PathVariable String tenantCode, @AuthenticationPrincipal IntegrationPrincipal principal) {
        require(principal, tenantCode, IntegrationScope.STATUS);
        var connection = service.dashboard(principal.tenant()).connection();
        if (connection != null) connection = service.refreshStatus(connection);
        return Map.of("store", principal.tenant().getCode(), "sessionStatus", connection == null ? "NOT_CONFIGURED" : connection.getStatus().name(),
                "phoneNumber", connection == null || connection.getPhoneNumber() == null ? "" : connection.getPhoneNumber());
    }

    @GetMapping("/groups")
    public Map<String,Object> groups(@PathVariable String tenantCode, @AuthenticationPrincipal IntegrationPrincipal principal) {
        require(principal, tenantCode, IntegrationScope.GROUPS);
        var dashboard = service.dashboard(principal.tenant());
        var result = dashboard.connection() == null ? java.util.List.<WhatsappGroup>of() : service.refreshGroups(dashboard.connection());
        return Map.of("store", principal.tenant().getCode(), "sessionStatus", dashboard.connection() == null ? "NOT_CONFIGURED" : dashboard.connection().getStatus().name(),
                "groups", result.stream().map(item -> Map.of("id", item.getWhatsappId(), "name", item.getName())).toList());
    }

    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object send(@PathVariable String tenantCode, @AuthenticationPrincipal IntegrationPrincipal principal,
                       @Valid @RequestBody WhatsappSendRequest request,
                       @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        require(principal, tenantCode, IntegrationScope.SEND);
        String signature = request.phone() + "|" + request.isGroup() + "|" + request.message();
        return idempotency.execute(principal, idempotencyKey, signature,
                () -> service.sendText(principal.tenant(), request.phone(), request.isGroup(), request.message()));
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object sendFile(@PathVariable String tenantCode, @AuthenticationPrincipal IntegrationPrincipal principal,
                           @RequestParam String phone, @RequestParam(defaultValue = "false") boolean isGroup,
                           @RequestPart MultipartFile file, @RequestParam(required = false) String caption) {
        require(principal, tenantCode, IntegrationScope.SEND);
        return service.sendFile(principal.tenant(), phone, isGroup, file, caption);
    }

    private void require(IntegrationPrincipal principal, String code, IntegrationScope scope) {
        if (principal == null || !principal.tenant().getCode().equals(code) || !principal.permits(scope)) throw new AccessDeniedException("La credencial no corresponde a esta tienda o no tiene el permiso requerido.");
        rateLimiter.check(principal.tokenId());
    }
}
