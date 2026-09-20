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

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findByBarcodeAndActiveTrue(String barcode);

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByBarcode(String barcode);

    @Query("""
            select p from Product p
            left join fetch p.category
            left join fetch p.supplier
            where p.id = :id
            """)
    Optional<Product> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from Product p
            left join p.category c
            where lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.code) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Product> search(@Param("q") String query, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            left join p.category c
            where (
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
    Page<Product> filter(@Param("q") String query,
                         @Param("name") String name,
                         @Param("brand") String brand,
                         @Param("categoryId") Long categoryId,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("active") Boolean active,
                         @Param("whatsapp") Boolean whatsapp,
                         Pageable pageable);

    @Query("""
            select p from Product p
            where p.active = true and (
                lower(p.name) like lower(concat('%', :q, '%'))
                or lower(p.code) like lower(concat('%', :q, '%'))
                or lower(coalesce(p.barcode, '')) like lower(concat('%', :q, '%'))
            )
            order by p.name asc
            """)
    List<Product> quickSearch(@Param("q") String query, Pageable pageable);

    @Query("select p from Product p where p.currentStock <= p.minimumStock and p.active = true order by p.currentStock asc")
    List<Product> findLowStock(Pageable pageable);

    @Query("select coalesce(sum(p.currentStock * p.purchaseCost), 0) from Product p where p.active = true")
    java.math.BigDecimal inventoryValue();

    @Query("select coalesce(sum(p.currentStock * p.salePrice), 0) from Product p where p.active = true")
    java.math.BigDecimal inventorySaleValue();

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            left join p.category c
            where p.active = true
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
    Page<Product> catalogSearch(@Param("q") String query, @Param("categoryId") Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            left join p.category c
            where p.active = true and p.promoted = true
            order by coalesce(p.promotionOrder, 999999), p.name asc
            """)
    List<Product> findCatalogPromotions(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndActiveTrue(Long id);

    long countByPromotedTrue();

    @Query("select coalesce(max(p.promotionOrder), 0) from Product p where p.promoted = true")
    Integer maxPromotionOrder();
}

