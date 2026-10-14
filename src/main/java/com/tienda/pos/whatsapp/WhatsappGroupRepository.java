package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WhatsappGroupRepository extends JpaRepository<WhatsappGroup, Long> {
    List<WhatsappGroup> findByTenantId(Long tenantId);
    List<WhatsappGroup> findByTenantIdAndAvailableTrueOrderByNameAsc(Long tenantId);
    Optional<WhatsappGroup> findByTenantIdAndWhatsappId(Long tenantId, String whatsappId);
    long countByTenantIdAndAvailableTrue(Long tenantId);
    void deleteByTenantId(Long tenantId);
}
