package com.tienda.pos.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TenantWhatsappConnectionRepository extends JpaRepository<TenantWhatsappConnection, Long> {
    @Query("select c from TenantWhatsappConnection c join fetch c.provider join fetch c.tenant where c.tenant.id = :tenantId")
    Optional<TenantWhatsappConnection> findByTenantIdWithDetails(Long tenantId);
    @Query("select c from TenantWhatsappConnection c join fetch c.provider join fetch c.tenant where c.id = :id")
    Optional<TenantWhatsappConnection> findByIdWithDetails(Long id);
    boolean existsByProviderIdAndSessionName(Long providerId, String sessionName);
    @Query("select c from TenantWhatsappConnection c join fetch c.provider join fetch c.tenant order by c.tenant.name")
    List<TenantWhatsappConnection> findAllWithDetails();
    long countByProviderId(Long providerId);
    long countByProviderIdAndStatus(Long providerId, WhatsappSessionStatus status);
}
