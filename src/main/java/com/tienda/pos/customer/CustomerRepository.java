package com.tienda.pos.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
    @Query("select c from Customer c where lower(concat(c.name, ' ', coalesce(c.lastName, ''))) like lower(concat('%', :query, '%')) order by c.name, c.lastName")
    List<Customer> search(@Param("query") String query);
    List<Customer> findByActiveTrueOrderByNameAscLastNameAsc();
}
