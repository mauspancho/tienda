# V9: compatibility with MySQL 8 and MariaDB

## Cause

V8 creates `uk_inventory_stock_product_warehouse (product_id, warehouse_id)`.
InnoDB also uses that index to enforce `fk_inventory_stock_product`.
The original V9 statement was:

```sql
alter table inventory_stock drop index uk_inventory_stock_product_warehouse;
```

MySQL rejects it with error 1553: the index is needed by a foreign key.
The replacement `(tenant_id, product_id, warehouse_id)` cannot support a foreign
key on `product_id`, because that column is not its first key part.

## Corrections and audit

- V9 explicitly adds `idx_inventory_stock_product (product_id)` before dropping
  the old unique key. The existing product foreign key is not removed or disabled.
- Each old unique key is dropped in the same ALTER TABLE that adds its replacement
  (product, category, expense_category, inventory_stock). This avoids a separately
  committed interval without uniqueness enforcement.
- The other removed indexes cover product code/barcode and category names. No
  foreign key in V1-V8 references those columns: references use the preserved IDs.
- Existing warehouse/business/branch/cashier FK-supporting indexes stay in place.
  Tenant-leading indexes do not replace those supporting indexes.
- Another real V9 failure affected existing sibling branches, warehouses and cash
  registers: all received the same code before adding unique constraints. The
  first record per parent keeps MATRIZ/PRINCIPAL/CAJA01; siblings receive stable
  ID-based codes. GROUP BY materializes the derived tables for the MySQL update.
- No database-specific IF NOT EXISTS index syntax, forced ALTER algorithm, foreign
  key disabling, or SQL-mode relaxation is used. MySQL and MariaDB are both tested.
- The tests inspect all FK-supporting indexes, preserve every V8 foreign key, and
  reject exact duplicate indexes (same table, columns and uniqueness).

## DDL and existing installations

MySQL 8 atomic DDL applies per statement, not to the whole Flyway migration.
Earlier statements can remain committed after V9 fails. MariaDB DDL also has
implicit commit boundaries. V9 is not a resumable/idempotent recovery script.

Do not blindly run Flyway repair, delete history rows, disable checksum validation,
or rerun V9 on a partially migrated database. For the reported disposable clean
installation, use a new empty schema with the corrected build. Never erase a
database containing real data. A failed installation with data requires a backup
and a schema/history audit before a tailored recovery.

Because V9 itself was corrected, installations where the original V9 already
succeeded have a checksum mismatch. Do not automatically repair those either:
compare and reconcile the actual schema (including the product support index)
before explicitly accepting a new checksum. No production database is modified
by the tests or CI.

## Reproducible verification

Java 21, Maven and a working Docker engine running Linux containers are required:

```sh
mvn clean test
mvn clean package
bash scripts/smoke-clean-jar.sh target/tienda-pos.jar
```

`TenantMigrationTest` starts disposable Testcontainers instances of
`mysql:8.0.36` and `mariadb:11.4.5`. No H2, local datasource configuration, fixed
database endpoint, reused container, external volume, or Docker-absent skip is
allowed for these tests. JDBC URLs come only from each container's mapped port.

For each engine the suite verifies an initially empty schema, all real migration
resources from V1 through the latest version, SUCCESS states in Flyway and its
history table, SHOW INDEX output, the original product FK, and a no-op second
migration. A populated V8 case reproduces error 1553, migrates sibling locations,
preserves stock/costs and existing FKs, and checks that invalid product references
and duplicate stock records are still rejected.

The Migration compatibility GitHub Actions workflow runs both Maven commands with
Docker on Ubuntu, starts the resulting JAR in an empty directory and retains logs,
Surefire reports and the tested JAR as artifacts. A machine without Docker cannot
pass the mandatory container tests; it is not treated as a successful verification.

The existing finance/favicon MVC tests explicitly select normal mode after the
setup environment processor. They no longer rely on a developer's installation
config being present. CI uses bash with pipefail so tee cannot hide a Maven failure.

References:
- https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html
- https://dev.mysql.com/doc/refman/8.0/en/atomic-ddl.html
- https://java.testcontainers.org/modules/databases/mysql/
