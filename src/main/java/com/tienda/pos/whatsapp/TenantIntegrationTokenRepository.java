package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TenantIntegrationTokenRepository extends JpaRepository<TenantIntegrationToken, Long> {
    Optional<TenantIntegrationToken> findByTokenHashAndActiveTrue(String tokenHash);
    List<TenantIntegrationToken> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<TenantIntegrationToken> findByIdAndTenantId(Long id, Long tenantId);
}
