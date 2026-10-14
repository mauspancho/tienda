package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;

public interface TenantWhatsappAnnouncementSettingsRepository extends JpaRepository<TenantWhatsappAnnouncementSettings, Long> {
    Optional<TenantWhatsappAnnouncementSettings> findByTenantId(Long tenantId);
    @Query("select s from TenantWhatsappAnnouncementSettings s join fetch s.tenant t where s.enabled = true order by t.code")
    List<TenantWhatsappAnnouncementSettings> findEnabledWithTenantOrderByCode();
}
