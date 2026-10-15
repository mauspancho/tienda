package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.product.Product;
import com.tienda.pos.product.ProductRepository;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.tenant.TenantRepository;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@NormalMode
public class ManualWhatsappPromotionPersistenceService {
    public record DeliveryTarget(Long deliveryId, String whatsappId, String groupName,
                                 ManualPromotionDeliveryStatus status, String error) { }
    public record Prepared(Long promotionId, boolean created, ManualPromotionStatus status,
                           int accepted, int failed, List<DeliveryTarget> deliveries) { }

    private final WhatsappManualPromotionRepository promotions;
    private final WhatsappManualPromotionDeliveryRepository deliveries;
    private final TenantRepository tenants;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final TenantWhatsappConnectionRepository connections;
    private final WhatsappGroupRepository groups;

    public ManualWhatsappPromotionPersistenceService(WhatsappManualPromotionRepository promotions,
                                                      WhatsappManualPromotionDeliveryRepository deliveries,
                                                      TenantRepository tenants, ProductRepository products,
                                                      AppUserRepository users,
                                                      TenantWhatsappConnectionRepository connections,
                                                      WhatsappGroupRepository groups) {
        this.promotions = promotions;
        this.deliveries = deliveries;
        this.tenants = tenants;
        this.products = products;
        this.users = users;
        this.connections = connections;
        this.groups = groups;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared prepare(Long tenantId, Long productId, Long userId, Long connectionId,
                            String requestKey, String message, List<Long> groupIds) {
        var existing = promotions.findByTenantIdAndRequestKey(tenantId, requestKey);
        if (existing.isPresent()) return existing(existing.get(), productId, message);

        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new DomainException("Tienda no encontrada."));
        Product product = products.findDetailedByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        AppUser user = users.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new DomainException("Usuario no autorizado para esta tienda."));
        TenantWhatsappConnection connection = connections.findByIdWithDetails(connectionId)
                .filter(value -> Objects.equals(value.getTenant().getId(), tenantId))
                .orElseThrow(() -> new DomainException("La conexión de WhatsApp no pertenece a esta tienda."));

        WhatsappManualPromotion promotion = new WhatsappManualPromotion();
        promotion.setTenant(tenant);
        promotion.setProduct(product);
        promotion.setUser(user);
        promotion.setConnection(connection);
        promotion.setRequestKey(requestKey);
        promotion.setMessage(message);
        promotion.setCreatedBy(user.getUsername());
        promotion.setUpdatedBy(user.getUsername());
        promotions.saveAndFlush(promotion);

        List<WhatsappManualPromotionDelivery> pending = new ArrayList<>();
        for (Long groupId : groupIds) {
            WhatsappGroup group = groups.findById(groupId)
                    .filter(value -> Objects.equals(value.getTenant().getId(), tenantId)
                            && Objects.equals(value.getConnection().getId(), connectionId)
                            && value.isAvailable() && value.isSelectedForAnnouncements())
                    .orElseThrow(() -> new DomainException("Uno de los grupos ya no está autorizado."));
            WhatsappManualPromotionDelivery delivery = new WhatsappManualPromotionDelivery();
            delivery.setPromotion(promotion);
            delivery.setGroup(group);
            delivery.setGroupName(group.getName());
            delivery.setRecipientType(ManualPromotionRecipientType.GROUP);
            delivery.setRecipientAddress(group.getWhatsappId());
            delivery.setCreatedBy(user.getUsername());
            delivery.setUpdatedBy(user.getUsername());
            pending.add(delivery);
        }
        deliveries.saveAll(pending);
        deliveries.flush();
        return snapshot(promotion, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared prepareNumber(Long tenantId, Long productId, Long userId, Long connectionId,
                                  String requestKey, String message, String normalizedNumber) {
        var existing = promotions.findByTenantIdAndRequestKey(tenantId, requestKey);
        if (existing.isPresent()) return existingNumber(existing.get(), productId, message, normalizedNumber);

        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new DomainException("Tienda no encontrada."));
        Product product = products.findDetailedByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new DomainException("Producto no encontrado."));
        AppUser user = users.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new DomainException("Usuario no autorizado para esta tienda."));
        TenantWhatsappConnection connection = connections.findByIdWithDetails(connectionId)
                .filter(value -> Objects.equals(value.getTenant().getId(), tenantId))
                .orElseThrow(() -> new DomainException("La conexión de WhatsApp no pertenece a esta tienda."));

        WhatsappManualPromotion promotion = createPromotion(tenant, product, user, connection, requestKey, message);
        WhatsappManualPromotionDelivery delivery = new WhatsappManualPromotionDelivery();
        delivery.setPromotion(promotion);
        delivery.setGroupName("+" + normalizedNumber);
        delivery.setRecipientType(ManualPromotionRecipientType.NUMBER);
        delivery.setRecipientAddress(normalizedNumber);
        delivery.setCreatedBy(user.getUsername());
        delivery.setUpdatedBy(user.getUsername());
        deliveries.saveAndFlush(delivery);
        return snapshot(promotion, true);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Prepared loadExisting(Long tenantId, String requestKey, Long productId, String message) {
        WhatsappManualPromotion promotion = promotions.findByTenantIdAndRequestKey(tenantId, requestKey)
                .orElseThrow(() -> new DomainException("No fue posible recuperar la solicitud repetida."));
        return existing(promotion, productId, message);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Prepared loadExistingNumber(Long tenantId, String requestKey, Long productId, String message,
                                       String normalizedNumber) {
        WhatsappManualPromotion promotion = promotions.findByTenantIdAndRequestKey(tenantId, requestKey)
                .orElseThrow(() -> new DomainException("No fue posible recuperar la solicitud repetida."));
        return existingNumber(promotion, productId, message, normalizedNumber);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(Long tenantId, Long deliveryId, WppConnectClient.SendResult result, String error) {
        WhatsappManualPromotionDelivery delivery = deliveries.findByIdAndPromotionTenantId(deliveryId, tenantId)
                .orElseThrow(() -> new DomainException("Resultado de promoción no encontrado."));
        if (delivery.getStatus() != ManualPromotionDeliveryStatus.PENDING) return;
        if (error == null && result != null && result.success()) {
            delivery.setStatus(ManualPromotionDeliveryStatus.ACCEPTED);
            delivery.setProviderMessageId(trim(result.messageId(), 255));
        } else {
            delivery.setStatus(ManualPromotionDeliveryStatus.FAILED);
            String detail = error != null ? error : result == null ? "WPPConnect no devolvió una respuesta."
                    : result.detail();
            delivery.setErrorMessage(trim(detail == null || detail.isBlank()
                    ? "WPPConnect rechazó la solicitud." : detail, 500));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared finish(Long tenantId, Long promotionId) {
        WhatsappManualPromotion promotion = promotions.findById(promotionId)
                .filter(value -> Objects.equals(value.getTenant().getId(), tenantId))
                .orElseThrow(() -> new DomainException("Promoción no encontrada."));
        List<WhatsappManualPromotionDelivery> values = deliveries.findByPromotionIdOrderByIdAsc(promotionId);
        int accepted = (int) values.stream().filter(value -> value.getStatus() == ManualPromotionDeliveryStatus.ACCEPTED).count();
        int failed = (int) values.stream().filter(value -> value.getStatus() == ManualPromotionDeliveryStatus.FAILED).count();
        promotion.setAcceptedCount(accepted);
        promotion.setFailedCount(failed);
        promotion.setStatus(accepted > 0 && failed == 0 ? ManualPromotionStatus.COMPLETED
                : accepted > 0 ? ManualPromotionStatus.PARTIAL : ManualPromotionStatus.FAILED);
        return snapshot(promotion, false);
    }

    private Prepared existing(WhatsappManualPromotion promotion, Long productId, String message) {
        if (!Objects.equals(promotion.getProduct().getId(), productId)
                || !Objects.equals(promotion.getMessage(), message)) {
            throw new DomainException("La clave de envío ya fue utilizada para otra promoción.");
        }
        return snapshot(promotion, false);
    }

    private Prepared existingNumber(WhatsappManualPromotion promotion, Long productId, String message,
                                    String normalizedNumber) {
        Prepared existing = existing(promotion, productId, message);
        boolean sameRecipient = existing.deliveries().size() == 1
                && Objects.equals(existing.deliveries().getFirst().whatsappId(), normalizedNumber)
                && deliveries.findByPromotionIdOrderByIdAsc(promotion.getId()).getFirst().getRecipientType()
                    == ManualPromotionRecipientType.NUMBER;
        if (!sameRecipient) throw new DomainException("La clave de envío ya fue utilizada para otro destinatario.");
        return existing;
    }

    private Prepared snapshot(WhatsappManualPromotion promotion, boolean created) {
        List<DeliveryTarget> targets = deliveries.findByPromotionIdOrderByIdAsc(promotion.getId()).stream()
                .map(value -> new DeliveryTarget(value.getId(), value.getRecipientAddress(),
                        value.getGroupName(), value.getStatus(), value.getErrorMessage()))
                .toList();
        return new Prepared(promotion.getId(), created, promotion.getStatus(),
                promotion.getAcceptedCount(), promotion.getFailedCount(), targets);
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private WhatsappManualPromotion createPromotion(Tenant tenant, Product product, AppUser user,
                                                     TenantWhatsappConnection connection, String requestKey,
                                                     String message) {
        WhatsappManualPromotion promotion = new WhatsappManualPromotion();
        promotion.setTenant(tenant);
        promotion.setProduct(product);
        promotion.setUser(user);
        promotion.setConnection(connection);
        promotion.setRequestKey(requestKey);
        promotion.setMessage(message);
        promotion.setCreatedBy(user.getUsername());
        promotion.setUpdatedBy(user.getUsername());
        return promotions.saveAndFlush(promotion);
    }
}
