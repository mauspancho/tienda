package com.tienda.pos.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcodeAndActiveTrue(String barcode);

    boolean existsByCode(String code);

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
}
