package com.tienda.pos.api.v1.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotencyRecord, Long> {
    Optional<ApiIdempotencyRecord> findByUsernameAndOperationNameAndKeyHash(
            String username, String operationName, String keyHash);
}
