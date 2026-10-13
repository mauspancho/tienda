package com.tienda.pos.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    long countByTenantId(Long tenantId);

    @EntityGraph(attributePaths = {"category", "supplier"})
    @Query("""
            select p from Product p
            left join p.category c
            where p.tenant.id = :tenantId and (
                :q = ''
                or lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.code) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.presentation, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(c.name, '')) like lower(concat('%', :q, '%'))
            )
              and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
              and (:brand = '' or lower(coalesce(p.brand, '')) like lower(concat('%', :brand, '%')))
              and (:categoryId is null or c.id = :categoryId)
              and (:minPrice is null or p.salePrice >= :minPrice)
              and (:maxPrice is null or p.salePrice <= :maxPrice)
              and (:active is null or p.active = :active)
              and (:whatsapp is null or p.promocionWhatsapp = :whatsapp)
            """)
    Page<Product> filter(@Param("tenantId") Long tenantId, @Param("q") String query,
                         @Param("name") String name,
                         @Param("brand") String brand,
                         @Param("categoryId") Long categoryId,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("active") Boolean active,
                         @Param("whatsapp") Boolean whatsapp,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"category", "supplier"})
    @Query("""
            select p from Product p
            left join p.category c
            where p.tenant.id = :tenantId and (
                :q = ''
                or lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.code) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.presentation, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(c.name, '')) like lower(concat('%', :q, '%'))
            )
              and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
              and (:brand = '' or lower(coalesce(p.brand, '')) like lower(concat('%', :brand, '%')))
              and (:brandListEmpty = true or lower(coalesce(p.brand, '')) in :brandList)
              and (:categoryId is null or c.id = :categoryId)
              and (:minPrice is null or p.salePrice >= :minPrice)
              and (:maxPrice is null or p.salePrice <= :maxPrice)
              and (:active is null or p.active = :active)
              and (:whatsapp is null or p.promocionWhatsapp = :whatsapp)
            """)
    Page<Product> filterForTable(@Param("tenantId") Long tenantId, @Param("q") String query,
                                 @Param("name") String name,
                                 @Param("brand") String brand,
                                 @Param("brandListEmpty") boolean brandListEmpty,
                                 @Param("brandList") List<String> brandList,
                                 @Param("categoryId") Long categoryId,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("active") Boolean active,
                                 @Param("whatsapp") Boolean whatsapp,
                                 Pageable pageable);

    @Query("""
            select distinct p.name from Product p
            where p.tenant.id = :tenantId and lower(p.name) like lower(concat('%', :q, '%'))
            order by p.name asc
            """)
    List<String> suggestNames(@Param("tenantId") Long tenantId, @Param("q") String query, Pageable pageable);

    @Query("""
            select distinct p.brand from Product p
            where p.tenant.id = :tenantId and p.brand is not null
              and p.brand <> ''
              and lower(p.brand) like lower(concat('%', :q, '%'))
            order by p.brand asc
            """)
    List<String> suggestBrands(@Param("tenantId") Long tenantId, @Param("q") String query, Pageable pageable);



    Optional<Product> findByTenantIdAndBarcode(Long tenantId, String barcode);

    Optional<Product> findByTenantIdAndBarcodeAndActiveTrue(Long tenantId, String barcode);

    Optional<Product> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndBarcode(Long tenantId, String barcode);

    @Query("""
            select p from Product p
            left join fetch p.category
            left join fetch p.supplier
            where p.id = :id and p.tenant.id = :tenantId
            """)
    Optional<Product> findDetailedByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.tenant.id = :tenantId")
    Optional<Product> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @EntityGraph(attributePaths = {"category", "supplier"})
    Page<Product> findByTenantIdOrderByNameAsc(Long tenantId, Pageable pageable);

    @Query("""
            select p from Product p
            left join p.category c
            where p.tenant.id = :tenantId
              and (
                  lower(p.name) like lower(concat('%', :q, '%'))
                  or lower(p.code) like lower(concat('%', :q, '%'))
                  or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<Product> search(@Param("tenantId") Long tenantId, @Param("q") String query, Pageable pageable);

    @Query("""
            select p from Product p
            where p.tenant.id = :tenantId and p.active = true and (
                lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.code) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
            )
            order by p.name asc
            """)
    List<Product> quickSearch(@Param("tenantId") Long tenantId, @Param("q") String query, Pageable pageable);

    @Query("""
            select p from InventoryStock s
            join s.product p
            where s.tenant.id = :tenantId and s.quantity <= s.minimumStock and p.active = true
            order by s.quantity asc
            """)
    List<Product> findLowStock(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("select coalesce(sum(s.quantity * p.purchaseCost), 0) from InventoryStock s join s.product p where s.tenant.id = :tenantId and p.active = true")
    java.math.BigDecimal inventoryValue(@Param("tenantId") Long tenantId);

    @Query("select coalesce(sum(s.quantity * p.salePrice), 0) from InventoryStock s join s.product p where s.tenant.id = :tenantId and p.active = true")
    java.math.BigDecimal inventorySaleValue(@Param("tenantId") Long tenantId);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            left join p.category c
            where p.tenant.id = :tenantId
              and p.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (
                  :q is null or :q = ''
                  or lower(p.name) like lower(concat('%', :q, '%'))
                  or lower(coalesce(p.brand, '')) like lower(concat('%', :q, '%'))
                  or lower(coalesce(p.presentation, '')) like lower(concat('%', :q, '%'))
                  or lower(coalesce(c.name, '')) like lower(concat('%', :q, '%'))
              )
            order by p.name asc
            """)
    Page<Product> catalogSearch(@Param("tenantId") Long tenantId, @Param("q") String query, @Param("categoryId") Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            left join p.category c
            where p.tenant.id = :tenantId and p.active = true and p.promoted = true
            order by coalesce(p.promotionOrder, 999999), p.name asc
            """)
    List<Product> findCatalogPromotions(@Param("tenantId") Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndTenantIdAndActiveTrue(Long id, Long tenantId);

    long countByTenantIdAndPromotedTrue(Long tenantId);

    @Query("select coalesce(max(p.promotionOrder), 0) from Product p where p.tenant.id = :tenantId and p.promoted = true")
    Integer maxPromotionOrder(@Param("tenantId") Long tenantId);
    List<Product> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
}
