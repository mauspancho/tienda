package com.tienda.pos.inventory;

import com.tienda.pos.product.Product;
import com.tienda.pos.warehouse.Warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    Optional<InventoryStock> findByProductAndWarehouseAndTenantId(Product product, Warehouse warehouse, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.product = :product and s.warehouse = :warehouse and s.tenant.id = :tenantId")
    Optional<InventoryStock> findByProductAndWarehouseAndTenantIdForUpdate(@Param("product") Product product,
                                                                           @Param("warehouse") Warehouse warehouse,
                                                                           @Param("tenantId") Long tenantId);
}