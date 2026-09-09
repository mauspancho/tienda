package com.tienda.pos.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Long> {
    Optional<BusinessSettings> findFirstByTenantIdOrderByIdAsc(Long tenantId);
}