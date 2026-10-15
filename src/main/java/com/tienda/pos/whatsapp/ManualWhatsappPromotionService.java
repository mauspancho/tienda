package com.tienda.pos.whatsapp;

import com.tienda.pos.common.CurrentUser;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductImageService;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.settings.BusinessSettings;
import com.tienda.pos.settings.BusinessSettingsRepository;
import com.tienda.pos.tenant.CurrentTenant;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@NormalMode
public class ManualWhatsappPromotionService {
    public static final int MAX_MESSAGE_LENGTH = 4000;

    public record Preview(boolean ready, boolean individualReady, String warning, String individualWarning,
                          String suggestedMessage,
                          List<String> groupNames, int groupCount) { }
    public record DeliveryResult(String groupName, String status, String error) { }
    public record Result(String status, int accepted, int failed, boolean duplicate,
                         boolean processing, List<DeliveryResult> groups) { }

    private final CurrentTenant currentTenant;
    private final ProductRepository products;
    private final ProductImageService images;
    private final WhatsappAnnouncementService announcements;
    private final WhatsappIntegrationService whatsapp;
    private final AppUserRepository users;
    private final BusinessSettingsRepository settings;
    private final ManualWhatsappPromotionPersistenceService persistence;

    public ManualWhatsappPromotionService(CurrentTenant currentTenant, ProductRepository products,
                                           ProductImageService images, WhatsappAnnouncementService announcements,
                                           WhatsappIntegrationService whatsapp, AppUserRepository users,
                                           BusinessSettingsRepository settings,
                                           ManualWhatsappPromotionPersistenceService persistence) {
        this.currentTenant = currentTenant;
        this.products = products;
        this.images = images;
        this.announcements = announcements;
        this.whatsapp = whatsapp;
        this.users = users;
        this.settings = settings;
        this.persistence = persistence;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Preview preview(Long productId) {
        Tenant tenant = currentTenant.get();
        Product product = products.findDetailedByIdAndTenantId(productId, tenant.getId())
                .orElseThrow(() -> new DomainException("Producto no encontrado para esta tienda."));
        String warning = null;
        String individualWarning = null;
        boolean individualReady = false;
        List<String> names = List.of();
        try {
            if (!product.isActive()) throw new DomainException("Activa el producto antes de promocionarlo.");
            if (product.getCurrentStock() == null || product.getCurrentStock().signum() <= 0) {
                throw new DomainException("Agrega existencia al producto antes de promocionarlo.");
            }
            requireImage(product);
            if (!whatsapp.isConnected(tenant)) throw new DomainException("WhatsApp no está conectado.");
            individualReady = true;
        } catch (DomainException ex) {
            warning = ex.getMessage();
            individualWarning = ex.getMessage();
        }
        if (individualReady) {
            try {
                names = announcements.manualPromotionGroups(tenant).stream().map(WhatsappGroup::getName).toList();
            } catch (DomainException ex) {
                warning = ex.getMessage();
            }
        }
        return new Preview(warning == null, individualReady, warning, individualWarning,
                suggestedMessage(product, tenant.getId()), names, names.size());
    }

    public Result send(Long productId, String message, String requestKey) {
        return send(productId, message, requestKey, false, null);
    }

    public Result send(Long productId, String message, String requestKey, boolean sendToNumber, String phone) {
        Tenant tenant = currentTenant.get();
        String cleanMessage = validateMessage(message);
        String cleanKey = validateRequestKey(requestKey);
        Product product = requireEligibleProduct(productId, tenant.getId());
        ProductImageService.ProductImagePayload image = requireImage(product);
        AppUser user = currentUser(tenant.getId());
        if (sendToNumber) {
            return sendToNumber(tenant, product, user, image, cleanMessage, cleanKey, phone);
        }
        List<WhatsappGroup> groups = announcements.manualPromotionGroups(tenant);
        Long connectionId = groups.getFirst().getConnection().getId();

        ManualWhatsappPromotionPersistenceService.Prepared prepared;
        try {
            prepared = persistence.prepare(tenant.getId(), product.getId(), user.getId(), connectionId,
                    cleanKey, cleanMessage, groups.stream().map(WhatsappGroup::getId).toList());
        } catch (DataIntegrityViolationException ex) {
            prepared = persistence.loadExisting(tenant.getId(), cleanKey, product.getId(), cleanMessage);
        }
        if (!prepared.created()) return result(prepared, true);

        for (var delivery : prepared.deliveries()) {
            try {
                WhatsappGroup authorized = announcements.requireManualPromotionGroup(tenant, delivery.whatsappId());
                WppConnectClient.SendResult sent = whatsapp.sendFile(tenant, authorized.getWhatsappId(), true,
                        image.filename(), image.bytes(), cleanMessage);
                persistence.recordResult(tenant.getId(), delivery.deliveryId(), sent, null);
            } catch (Exception ex) {
                persistence.recordResult(tenant.getId(), delivery.deliveryId(), null, safeError(ex));
            }
        }
        return result(persistence.finish(tenant.getId(), prepared.promotionId()), false);
    }

    private Result sendToNumber(Tenant tenant, Product product, AppUser user,
                                ProductImageService.ProductImagePayload image, String message,
                                String requestKey, String phone) {
        String normalizedNumber = whatsapp.normalizeIndividualDestination(phone);
        TenantWhatsappConnection connection = whatsapp.requireConnectedConnection(tenant);
        ManualWhatsappPromotionPersistenceService.Prepared prepared;
        try {
            prepared = persistence.prepareNumber(tenant.getId(), product.getId(), user.getId(), connection.getId(),
                    requestKey, message, normalizedNumber);
        } catch (DataIntegrityViolationException ex) {
            prepared = persistence.loadExistingNumber(tenant.getId(), requestKey, product.getId(), message,
                    normalizedNumber);
        }
        if (!prepared.created()) return result(prepared, true);

        var delivery = prepared.deliveries().getFirst();
        try {
            WppConnectClient.SendResult sent = whatsapp.sendFile(tenant, normalizedNumber, false,
                    image.filename(), image.bytes(), message);
            persistence.recordResult(tenant.getId(), delivery.deliveryId(), sent, null);
        } catch (Exception ex) {
            persistence.recordResult(tenant.getId(), delivery.deliveryId(), null, safeError(ex));
        }
        return result(persistence.finish(tenant.getId(), prepared.promotionId()), false);
    }

    private Product requireEligibleProduct(Long productId, Long tenantId) {
        Product product = products.findDetailedByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado para esta tienda."));
        if (!product.isActive()) throw new DomainException("Solo se pueden promocionar productos activos.");
        if (product.getCurrentStock() == null || product.getCurrentStock().signum() <= 0) {
            throw new DomainException("El producto debe tener existencia para poder promocionarlo.");
        }
        return product;
    }

    private ProductImageService.ProductImagePayload requireImage(Product product) {
        if (!images.isLocalImage(product.getImageUrl())) {
            throw new DomainException("Agrega una imagen local válida al producto antes de promocionarlo.");
        }
        return images.readLocalImage(product.getImageUrl());
    }

    private AppUser currentUser(Long tenantId) {
        String username = CurrentUser.authenticatedUsername()
                .orElseThrow(() -> new DomainException("La sesión de usuario no es válida."));
        return users.findByUsernameWithTenant(username)
                .filter(value -> value.isActive() && value.getTenant() != null
                        && value.getTenant().getId().equals(tenantId) && value.hasRole("ROLE_ADMIN"))
                .orElseThrow(() -> new DomainException("No tienes permiso para enviar promociones."));
    }

    private String suggestedMessage(Product product, Long tenantId) {
        BusinessSettings configured = settings.findFirstByTenantIdOrderByIdAsc(tenantId).orElse(null);
        String symbol = configured == null || configured.getCurrencySymbol() == null ? "$" : configured.getCurrencySymbol();
        String currency = configured == null || configured.getCurrency() == null ? "MXN" : configured.getCurrency();
        String description = product.getName();
        if (product.getPresentation() != null && !product.getPresentation().isBlank()) {
            description += " " + product.getPresentation().trim();
        }
        return "📢 ¡Tenemos disponible " + description + "!\n\n💲 Precio: " + symbol
                + MoneyUtils.money(product.getSalePrice()) + " " + currency
                + "\n\n🚚 Contamos con servicio a domicilio.\n\n¡Haz tu pedido por WhatsApp!";
    }

    private String validateMessage(String message) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty()) throw new DomainException("Escribe el mensaje de la promoción.");
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            throw new DomainException("El mensaje no puede superar " + MAX_MESSAGE_LENGTH + " caracteres.");
        }
        return clean;
    }

    private String validateRequestKey(String requestKey) {
        String clean = requestKey == null ? "" : requestKey.trim();
        if (!clean.matches("[A-Za-z0-9_-]{16,80}")) {
            throw new DomainException("La clave de control del envío no es válida. Vuelve a abrir la promoción.");
        }
        return clean;
    }

    private Result result(ManualWhatsappPromotionPersistenceService.Prepared prepared, boolean duplicate) {
        boolean processing = prepared.status() == ManualPromotionStatus.PROCESSING;
        return new Result(prepared.status().name(), prepared.accepted(), prepared.failed(), duplicate, processing,
                prepared.deliveries().stream().map(value -> new DeliveryResult(value.groupName(),
                        value.status().name(), value.error())).toList());
    }

    private String safeError(Exception ex) {
        String message = ex instanceof DomainException ? ex.getMessage() : "No fue posible procesar el envío con WPPConnect.";
        if (message == null || message.isBlank()) message = "WPPConnect rechazó la solicitud.";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
