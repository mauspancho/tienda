package com.tienda.pos.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items", "items.product"})
    Optional<Sale> findByTenantIdAndFolio(Long tenantId, String folio);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items", "items.product"})
    Optional<Sale> findByTenantIdAndFolioAndCashierUsername(Long tenantId, String folio, String username);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items", "items.product"})
    Page<Sale> findByTenantIdOrderBySaleDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items", "items.product"})
    Page<Sale> findByTenantIdAndCashierUsernameOrderBySaleDateDesc(Long tenantId, String username, Pageable pageable);

    long countByTenantIdAndSaleDateBetweenAndStatus(Long tenantId, LocalDateTime start, LocalDateTime end, SaleStatus status);

    long countByTenantIdAndCashierUsernameAndSaleDateBetweenAndStatus(Long tenantId, String username, LocalDateTime start, LocalDateTime end, SaleStatus status);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.tenant.id = :tenantId and s.saleDate between :start and :end and s.status = 'COMPLETED'")
    BigDecimal totalSales(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.tenant.id = :tenantId and s.cashier.username = :username and s.saleDate between :start and :end and s.status = 'COMPLETED'")
    BigDecimal totalSalesByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.profit), 0) from SaleItem i where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal grossProfit(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.profit), 0) from SaleItem i where i.sale.tenant.id = :tenantId and i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal grossProfitByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.quantity), 0) from SaleItem i where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal soldUnits(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.quantity), 0) from SaleItem i where i.sale.tenant.id = :tenantId and i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal soldUnitsByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select date(s.saleDate), coalesce(sum(s.total), 0)
            from Sale s
            where s.tenant.id = :tenantId and s.saleDate >= :start and s.status = 'COMPLETED'
            group by date(s.saleDate)
            order by date(s.saleDate)
            """)
    List<Object[]> dailySalesSince(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start);

    @Query("""
            select date(s.saleDate), coalesce(sum(s.total), 0)
            from Sale s
            where s.tenant.id = :tenantId and s.cashier.username = :username and s.saleDate >= :start and s.status = 'COMPLETED'
            group by date(s.saleDate)
            order by date(s.saleDate)
            """)
    List<Object[]> dailySalesSinceByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start);

    @Query("""
            select date(i.sale.saleDate), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by date(i.sale.saleDate)
            order by date(i.sale.saleDate)
            """)
    List<Object[]> dailyGrossProfitBetween(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select date(i.sale.saleDate), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by date(i.sale.saleDate)
            order by date(i.sale.saleDate)
            """)
    List<Object[]> dailyGrossProfitBetweenByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.unitCost * i.quantity), 0),
                   coalesce(sum(i.profit), 0),
                   count(distinct s.id),
                   coalesce(sum(i.quantity), 0)
            from Sale s join s.items i
            where s.tenant.id = :tenantId and s.saleDate between :start and :end and s.status = 'COMPLETED'
            """)
    Object[] financeTotals(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select date(s.saleDate),
                   coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.unitCost * i.quantity), 0),
                   coalesce(sum(i.profit), 0),
                   count(distinct s.id),
                   coalesce(sum(i.quantity), 0)
            from Sale s join s.items i
            where s.tenant.id = :tenantId and s.saleDate between :start and :end and s.status = 'COMPLETED'
            group by date(s.saleDate)
            order by date(s.saleDate)
            """)
    List<Object[]> dailyFinanceTotals(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select i.productNameSnapshot, coalesce(sum(i.quantity), 0), coalesce(sum(i.subtotal), 0), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.quantity) desc
            """)
    List<Object[]> topProducts(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("""
            select i.productNameSnapshot, coalesce(sum(i.quantity), 0), coalesce(sum(i.subtotal), 0), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.quantity) desc
            """)
    List<Object[]> topProductsByCashier(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("""
            select i.productNameSnapshot,
                   coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.quantity), 0),
                   coalesce(sum(i.unitCost * i.quantity), 0),
                   coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.profit) desc
            """)
    List<Object[]> profitableProducts(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("""
            select i.productNameSnapshot,
                   coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.quantity), 0),
                   coalesce(sum(i.unitCost * i.quantity), 0),
                   coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.quantity) desc
            """)
    List<Object[]> mostSoldProducts(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("""
            select i.productNameSnapshot,
                   coalesce(sum(i.subtotal), 0),
                   coalesce(sum(i.quantity), 0),
                   coalesce(sum(i.unitCost * i.quantity), 0),
                   coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.tenant.id = :tenantId and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.subtotal) desc
            """)
    List<Object[]> topBillingProducts(@Param("tenantId") Long tenantId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}