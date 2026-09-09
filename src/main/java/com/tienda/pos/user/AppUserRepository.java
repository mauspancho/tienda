package com.tienda.pos.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = "tenant")
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findByUsernameWithTenant(@Param("username") String username);

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    Page<AppUser> findByTenantIdOrderByUsernameAsc(Long tenantId, Pageable pageable);

    boolean existsByUsername(String username);
}