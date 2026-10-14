package com.tienda.pos.whatsapp.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformAutomationIdempotencyRepository extends JpaRepository<PlatformAutomationIdempotency, Long> {
    Optional<PlatformAutomationIdempotency> findByTokenIdAndTenantIdAndOperationNameAndKeyHash(
            Long tokenId, Long tenantId, String operationName, String keyHash);
}
