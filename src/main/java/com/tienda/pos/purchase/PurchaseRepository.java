package com.tienda.pos.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    @EntityGraph(attributePaths = {"supplier"})
    Page<Purchase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"supplier"})
    Page<Purchase> findByFundingSourceAndPurchaseDateBetweenOrderByPurchaseDateDesc(PurchaseFundingSource fundingSource, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select coalesce(sum(p.total), 0) from Purchase p where p.purchaseDate between :from and :to and p.status = 'CONFIRMED'")
    BigDecimal totalBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(p.total), 0) from Purchase p where p.purchaseDate between :from and :to and p.status = 'CONFIRMED' and p.fundingSource = :source")
    BigDecimal totalByFundingSourceBetween(@Param("source") PurchaseFundingSource source, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select p.purchaseDate, coalesce(sum(p.total), 0)
            from Purchase p
            where p.purchaseDate between :from and :to and p.status = 'CONFIRMED'
            group by p.purchaseDate
            order by p.purchaseDate
            """)
    List<Object[]> dailyTotalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select p.purchaseDate, coalesce(sum(p.total), 0)
            from Purchase p
            where p.purchaseDate between :from and :to and p.status = 'CONFIRMED' and p.fundingSource = :source
            group by p.purchaseDate
            order by p.purchaseDate
            """)
    List<Object[]> dailyTotalsByFundingSourceBetween(@Param("source") PurchaseFundingSource source, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select year(p.purchaseDate), month(p.purchaseDate), coalesce(sum(p.total), 0)
            from Purchase p
            where p.purchaseDate between :from and :to and p.status = 'CONFIRMED' and p.fundingSource = :source
            group by year(p.purchaseDate), month(p.purchaseDate)
            order by year(p.purchaseDate), month(p.purchaseDate)
            """)
    List<Object[]> monthlyTotalsByFundingSourceBetween(@Param("source") PurchaseFundingSource source, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
