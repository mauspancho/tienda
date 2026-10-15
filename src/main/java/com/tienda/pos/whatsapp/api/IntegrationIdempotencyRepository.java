package com.tienda.pos.whatsapp.api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IntegrationIdempotencyRepository extends JpaRepository<IntegrationIdempotencyRecord, Long> {
    Optional<IntegrationIdempotencyRecord> findByTenantIdAndTokenIdAndOperationNameAndKeyHash(Long tenantId, Long tokenId, String operationName, String keyHash);
}
