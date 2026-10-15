package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsappManualPromotionRepository extends JpaRepository<WhatsappManualPromotion, Long> {
    @EntityGraph(attributePaths = {"tenant", "product", "user", "connection"})
    Optional<WhatsappManualPromotion> findByTenantIdAndRequestKey(Long tenantId, String requestKey);
}
