package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface WhatsappGroupRepository extends JpaRepository<WhatsappGroup, Long> {
    List<WhatsappGroup> findByTenantId(Long tenantId);
    List<WhatsappGroup> findByTenantIdOrderByNameAsc(Long tenantId);
    List<WhatsappGroup> findByTenantIdAndAvailableTrueOrderByNameAsc(Long tenantId);
    List<WhatsappGroup> findByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrueOrderByNameAsc(Long tenantId);
    List<WhatsappGroup> findByTenantIdAndConnectionIdAndSelectedForAnnouncementsTrueAndAvailableTrueOrderByNameAsc(
            Long tenantId, Long connectionId);
    long countByTenantIdAndSelectedForAnnouncementsTrue(Long tenantId);
    long countByTenantIdAndSelectedForAnnouncementsTrueAndAvailableTrue(Long tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from WhatsappGroup g join fetch g.connection where g.tenant.id = :tenantId order by g.id")
    List<WhatsappGroup> findByTenantIdForUpdate(Long tenantId);
    Optional<WhatsappGroup> findByTenantIdAndWhatsappId(Long tenantId, String whatsappId);
    long countByTenantIdAndAvailableTrue(Long tenantId);
    @Query("select max(g.refreshedAt) from WhatsappGroup g where g.tenant.id = :tenantId and g.available = true")
    Optional<LocalDateTime> findLatestRefreshedAt(Long tenantId);
    void deleteByTenantId(Long tenantId);
}
