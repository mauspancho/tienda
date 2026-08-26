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

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items"})
    Optional<Sale> findByFolio(String folio);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment", "items"})
    Optional<Sale> findByFolioAndCashierUsername(String folio, String username);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment"})
    Page<Sale> findAllByOrderBySaleDateDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"cashier", "customer", "payment"})
    Page<Sale> findByCashierUsernameOrderBySaleDateDesc(String username, Pageable pageable);

    long countBySaleDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, SaleStatus status);

    long countByCashierUsernameAndSaleDateBetweenAndStatus(String username, LocalDateTime start, LocalDateTime end, SaleStatus status);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.saleDate between :start and :end and s.status = 'COMPLETED'")
    BigDecimal totalSales(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.cashier.username = :username and s.saleDate between :start and :end and s.status = 'COMPLETED'")
    BigDecimal totalSalesByCashier(@Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.profit), 0) from SaleItem i where i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal grossProfit(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.profit), 0) from SaleItem i where i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal grossProfitByCashier(@Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.quantity), 0) from SaleItem i where i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal soldUnits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(i.quantity), 0) from SaleItem i where i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'")
    BigDecimal soldUnitsByCashier(@Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            select date(s.saleDate), coalesce(sum(s.total), 0)
            from Sale s
            where s.saleDate >= :start and s.status = 'COMPLETED'
            group by date(s.saleDate)
            order by date(s.saleDate)
            """)
    List<Object[]> dailySalesSince(@Param("start") LocalDateTime start);

    @Query("""
            select date(s.saleDate), coalesce(sum(s.total), 0)
            from Sale s
            where s.cashier.username = :username and s.saleDate >= :start and s.status = 'COMPLETED'
            group by date(s.saleDate)
            order by date(s.saleDate)
            """)
    List<Object[]> dailySalesSinceByCashier(@Param("username") String username, @Param("start") LocalDateTime start);

    @Query("""
            select i.productNameSnapshot, coalesce(sum(i.quantity), 0), coalesce(sum(i.subtotal), 0), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.quantity) desc
            """)
    List<Object[]> topProducts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("""
            select i.productNameSnapshot, coalesce(sum(i.quantity), 0), coalesce(sum(i.subtotal), 0), coalesce(sum(i.profit), 0)
            from SaleItem i
            where i.sale.cashier.username = :username and i.sale.saleDate between :start and :end and i.sale.status = 'COMPLETED'
            group by i.productNameSnapshot
            order by sum(i.quantity) desc
            """)
    List<Object[]> topProductsByCashier(@Param("username") String username, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}
