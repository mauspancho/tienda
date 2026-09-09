package com.tienda.pos.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findFirstByTenantIdAndActiveTrueOrderByIdAsc(Long tenantId);
}