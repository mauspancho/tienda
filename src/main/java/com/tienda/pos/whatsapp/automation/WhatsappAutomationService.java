package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.tenant.TenantRepository;
import com.tienda.pos.whatsapp.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

@Service
@NormalMode
public class WhatsappAutomationService {
    public record StoreDto(String tenantCode, String tenantName, String whatsappStatus,
                           boolean announcementsEnabled, long selectedGroups, boolean ready) { }
    public record GroupDto(String id, String name, boolean isGroup) { }
    public record ProductDto(Long id, String code, String name, BigDecimal salePrice, BigDecimal currentStock,
                             String presentation, String brand, String unit, Long categoryId, String categoryName,
                             String imageUrl, boolean promocionWhatsapp) { }
    public record PageDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) { }

    private final TenantRepository tenants;
    private final TenantWhatsappAnnouncementSettingsRepository settings;
    private final WhatsappAnnouncementService announcements;
    private final ProductRepository products;
    private final WhatsappIntegrationService whatsapp;

    public WhatsappAutomationService(TenantRepository tenants, TenantWhatsappAnnouncementSettingsRepository settings,
                                     WhatsappAnnouncementService announcements, ProductRepository products,
                                     WhatsappIntegrationService whatsapp) {
        this.tenants = tenants; this.settings = settings; this.announcements = announcements;
        this.products = products; this.whatsapp = whatsapp;
    }

    @Transactional(readOnly = true)
    public List<StoreDto> eligibleStores() {
        return settings.findEnabledWithTenantOrderByCode().stream()
                .map(value -> announcements.view(value.getTenant()))
                .filter(WhatsappAnnouncementService.View::ready)
                .map(view -> new StoreDto(view.tenant().getCode(), view.tenant().getName(),
                        view.connection().getStatus().name(), true, view.selectedAvailableGroups(), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupDto> groups(String tenantCode) {
        Tenant tenant = readyTenant(tenantCode);
        return announcements.selectedAvailable(tenant).stream()
                .map(group -> new GroupDto(group.getWhatsappId(), group.getName(), true)).toList();
    }

    @Transactional(readOnly = true)
    public PageDto<ProductDto> promotions(String tenantCode, Pageable pageable) {
        Tenant tenant = readyTenant(tenantCode);
        return map(products.findWhatsappPromotions(tenant.getId(), pageable));
    }

    @Transactional(readOnly = true)
    public PageDto<ProductDto> catalog(String tenantCode, Pageable pageable) {
        Tenant tenant = readyTenant(tenantCode);
        return map(products.findWhatsappCatalog(tenant.getId(), pageable));
    }

    @Transactional(readOnly = true)
    public Tenant readyTenant(String code) {
        Tenant tenant = tenants.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada."));
        WhatsappAnnouncementService.View view = announcements.view(tenant);
        if (!view.ready()) throw new com.tienda.pos.exception.DomainException("La tienda no está apta para automatización: " + view.status().name());
        return tenant;
    }

    @Transactional(readOnly = true)
    public WppConnectClient.SendResult sendText(Tenant tenant, String groupId, String message) {
        WhatsappGroup group = announcements.requireAuthorizedGroup(tenant, groupId);
        return whatsapp.sendText(tenant, group.getWhatsappId(), true, message);
    }

    @Transactional(readOnly = true)
    public WppConnectClient.SendResult sendFile(Tenant tenant, String groupId, MultipartFile file, String caption) {
        WhatsappGroup group = announcements.requireAuthorizedGroup(tenant, groupId);
        return whatsapp.sendFile(tenant, group.getWhatsappId(), true, file, caption);
    }

    private PageDto<ProductDto> map(Page<Product> page) {
        List<ProductDto> items = page.getContent().stream().map(product -> new ProductDto(
                product.getId(), product.getCode(), product.getName(), product.getSalePrice(), product.getCurrentStock(),
                product.getPresentation(), blankBrand(product.getBrand()), product.getUnit().name(),
                product.getCategory() == null ? null : product.getCategory().getId(),
                product.getCategory() == null ? null : product.getCategory().getName(), product.getImageUrl(),
                product.isPromocionWhatsapp())).toList();
        return new PageDto<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    private String blankBrand(String brand) { return brand == null || brand.isBlank() ? "SIN MARCA" : brand; }
}
