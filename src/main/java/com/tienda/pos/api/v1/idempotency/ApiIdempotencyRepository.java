package com.tienda.pos.api.v1.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotencyRecord, Long> {
    Optional<ApiIdempotencyRecord> findByTenantIdAndUserIdAndOperationNameAndKeyHash(
            Long tenantId, Long userId, String operationName, String keyHash);
}
