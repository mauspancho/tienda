package com.tienda.pos.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByTenantIdAndName(Long tenantId, String name);
    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    @org.springframework.data.jpa.repository.Query("select c from Customer c where c.tenant.id = :tenantId and (lower(c.name) like lower(concat('%', :q, '%')) or lower(coalesce(c.lastName, '')) like lower(concat('%', :q, '%')) or coalesce(c.phone, '') like concat('%', :q, '%')) order by c.name, c.id")
    java.util.List<Customer> search(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, @org.springframework.data.repository.query.Param("q") String query);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Customer c where c.id = :id and c.tenant.id = :tenantId")
    Optional<Customer> findByIdAndTenantIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    java.util.List<Customer> findByTenantIdAndActiveTrueOrderByNameAscLastNameAsc(Long tenantId);
}
