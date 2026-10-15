package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappManualPromotionDeliveryRepository extends JpaRepository<WhatsappManualPromotionDelivery, Long> {
    @EntityGraph(attributePaths = "group")
    List<WhatsappManualPromotionDelivery> findByPromotionIdOrderByIdAsc(Long promotionId);
    Optional<WhatsappManualPromotionDelivery> findByIdAndPromotionTenantId(Long id, Long tenantId);
}
