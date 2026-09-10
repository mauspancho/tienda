package com.tienda.pos.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @Query("select u.username from AppUser u where u.tenant.id = :tenantId and not exists "
            + "(select r from u.roles r where r.name = 'ROLE_PLATFORM_ADMIN')")
    java.util.List<String> findNonPlatformUsernamesByTenantId(@Param("tenantId") Long tenantId);

    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = {"tenant", "roles"})
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findByUsernameWithTenant(@Param("username") String username);

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    Page<AppUser> findByTenantIdOrderByUsernameAsc(Long tenantId, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("select u from AppUser u where u.tenant.id = :tenantId and not exists "
            + "(select r from u.roles r where r.name = 'ROLE_PLATFORM_ADMIN') order by u.username")
    Page<AppUser> findManagedUsers(@Param("tenantId") Long tenantId, Pageable pageable);
}
