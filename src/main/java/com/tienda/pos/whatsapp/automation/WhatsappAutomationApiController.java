package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.whatsapp.WppConnectClient;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.MessageDigest;
import java.util.*;

@RestController
@NormalMode
@RequestMapping("/api/automation/v1/whatsapp")
public class WhatsappAutomationApiController {
    private final WhatsappAutomationService service;
    private final AutomationRateLimiter rateLimiter;
    private final AutomationIdempotencyService idempotency;
    public WhatsappAutomationApiController(WhatsappAutomationService service, AutomationRateLimiter rateLimiter,
                                           AutomationIdempotencyService idempotency) {
        this.service = service; this.rateLimiter = rateLimiter; this.idempotency = idempotency;
    }

    @GetMapping("/stores/eligible")
    public Map<String,Object> eligible(@AuthenticationPrincipal AutomationPrincipal principal) {
        require(principal, AutomationScope.ANNOUNCEMENTS_READ);
        return Map.of("stores", service.eligibleStores());
    }

    @GetMapping("/stores/{tenantCode}/groups")
    public Map<String,Object> groups(@PathVariable String tenantCode, @AuthenticationPrincipal AutomationPrincipal principal) {
        require(principal, AutomationScope.ANNOUNCEMENTS_READ);
        return Map.of("tenantCode", tenantCode, "announcementsEnabled", true, "groups", service.groups(tenantCode));
    }

    @GetMapping("/stores/{tenantCode}/products/promotions")
    public Map<String,Object> promotions(@PathVariable String tenantCode, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "100") int size,
                                         @AuthenticationPrincipal AutomationPrincipal principal) {
        require(principal, AutomationScope.ANNOUNCEMENTS_READ);
        return pageResponse(tenantCode, service.promotions(tenantCode, page(page, size)));
    }

    @GetMapping("/stores/{tenantCode}/products/catalog")
    public Map<String,Object> catalog(@PathVariable String tenantCode, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "100") int size,
                                      @AuthenticationPrincipal AutomationPrincipal principal) {
        require(principal, AutomationScope.ANNOUNCEMENTS_READ);
        return pageResponse(tenantCode, service.catalog(tenantCode, page(page, size)));
    }

    @PostMapping(value = "/stores/{tenantCode}/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WppConnectClient.SendResult send(@PathVariable String tenantCode,
                                             @AuthenticationPrincipal AutomationPrincipal principal,
                                             @Valid @RequestBody AutomationSendRequest request,
                                             @RequestHeader(name = "Idempotency-Key") String key) {
        require(principal, AutomationScope.ANNOUNCEMENTS_SEND);
        var tenant = service.readyTenant(tenantCode);
        String signature = "TEXT|" + tenantCode + "|" + request.groupId() + "|" + request.message();
        return idempotency.execute(principal, tenant, key, signature,
                () -> service.sendText(tenant, request.groupId(), request.message()));
    }

    @PostMapping(value = "/stores/{tenantCode}/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WppConnectClient.SendResult sendFile(@PathVariable String tenantCode,
                                                 @AuthenticationPrincipal AutomationPrincipal principal,
                                                 @RequestParam String groupId, @RequestPart MultipartFile file,
                                                 @RequestParam(required = false) String caption,
                                                 @RequestHeader(name = "Idempotency-Key") String key) {
        require(principal, AutomationScope.ANNOUNCEMENTS_SEND);
        var tenant = service.readyTenant(tenantCode);
        String signature = "FILE|" + tenantCode + "|" + groupId + "|" + Objects.toString(caption, "") + "|" + digest(file);
        return idempotency.execute(principal, tenant, key, signature,
                () -> service.sendFile(tenant, groupId, file, caption));
    }

    private void require(AutomationPrincipal principal, AutomationScope scope) {
        if (principal == null || !principal.permits(scope)) throw new AccessDeniedException("Permiso de automatización insuficiente.");
        rateLimiter.check(principal.tokenId());
    }
    private PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 250) throw new IllegalArgumentException("page debe ser >= 0 y size debe estar entre 1 y 250.");
        return PageRequest.of(page, size);
    }
    private Map<String,Object> pageResponse(String tenantCode, WhatsappAutomationService.PageDto<?> value) {
        List<?> items = value.items().stream().map(item -> item instanceof WhatsappAutomationService.ProductDto p
                ? new WhatsappAutomationService.ProductDto(p.id(), p.code(), p.name(), p.salePrice(), p.currentStock(),
                p.presentation(), p.brand(), p.unit(), p.categoryId(), p.categoryName(), absoluteImage(p.imageUrl()), p.promocionWhatsapp()) : item).toList();
        Map<String,Object> result = new LinkedHashMap<>(); result.put("tenantCode", tenantCode); result.put("products", items);
        result.put("page", value.page()); result.put("size", value.size()); result.put("totalElements", value.totalElements()); result.put("totalPages", value.totalPages());
        return result;
    }
    private String absoluteImage(String value) {
        if (value == null || value.isBlank() || value.startsWith("http://") || value.startsWith("https://")) return value;
        String path = value.startsWith("/") ? value : "/" + value;
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUriString();
    }
    private String digest(MultipartFile file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch (Exception ex) { throw new IllegalArgumentException("No fue posible leer el archivo.", ex); }
    }
}
