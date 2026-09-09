package com.tienda.pos.commercial;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialMigrationTest {

    @Test
    void commercialMigrationDefinesDefaultStructureAndInventoryStockBackfill() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V8__commercial_structure_and_inventory_stock.sql"));

        assertThat(migration).contains("create table business");
        assertThat(migration).contains("create table branch");
        assertThat(migration).contains("create table warehouse");
        assertThat(migration).contains("create table cash_register");
        assertThat(migration).contains("create table inventory_stock");
        assertThat(migration).contains("unique key uk_inventory_stock_product_warehouse");
        assertThat(migration).contains("from product p");
        assertThat(migration).contains("p.current_stock, p.minimum_stock");
        assertThat(migration).contains("modify branch_id bigint not null");
        assertThat(migration).contains("modify warehouse_id bigint not null");
        assertThat(migration).contains("fk_inventory_movement_warehouse");
    }
}