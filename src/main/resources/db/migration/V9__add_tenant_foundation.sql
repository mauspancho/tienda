create table tenant (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    code varchar(60) not null,
    name varchar(160) not null,
    active bit not null,
    primary key (id),
    unique key uk_tenant_code (code)
) engine=InnoDB default charset=utf8mb4;

insert into tenant(version, created_at, updated_at, code, name, active)
select 0, current_timestamp, current_timestamp, 'default', coalesce(nullif(store_name, ''), 'Tienda POS'), true
from business_settings
where not exists (select 1 from tenant)
order by id
limit 1;

insert into tenant(version, created_at, updated_at, code, name, active)
select 0, current_timestamp, current_timestamp, 'default', 'Tienda POS', true
where not exists (select 1 from tenant);

alter table app_user add column tenant_id bigint null;
alter table business
    add column tenant_id bigint null,
    add column legal_name varchar(200) null,
    add column phone varchar(255) null,
    add column email varchar(255) null;
alter table branch
    add column tenant_id bigint null,
    add column code varchar(60) null,
    add column phone varchar(255) null,
    add column timezone varchar(255) null;
alter table warehouse
    add column tenant_id bigint null,
    add column code varchar(60) null;
alter table cash_register
    add column tenant_id bigint null,
    add column code varchar(60) null;
alter table category add column tenant_id bigint null;
alter table supplier add column tenant_id bigint null;
alter table customer add column tenant_id bigint null;
alter table product add column tenant_id bigint null;
alter table purchase add column tenant_id bigint null;
alter table sale add column tenant_id bigint null;
alter table inventory_movement add column tenant_id bigint null;
alter table cash_register_session add column tenant_id bigint null;
alter table cash_movement add column tenant_id bigint null;
alter table expense_category add column tenant_id bigint null;
alter table expense add column tenant_id bigint null;
alter table business_settings
    add column tenant_id bigint null,
    add column business_id bigint null;
alter table capital_movement add column tenant_id bigint null;
alter table inventory_stock add column tenant_id bigint null;
alter table audit_log add column tenant_id bigint null;

update app_user set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update business set tenant_id = (select id from tenant order by id limit 1), legal_name = coalesce(legal_name, name) where tenant_id is null;
-- Keep the default code for the first location; legacy siblings need distinct codes.
-- GROUP BY materializes each derived table on MySQL (avoids target-table error 1093).
update branch br
join (select business_id, min(id) as first_id from branch group by business_id) first_branch
    on first_branch.business_id = br.business_id
set br.tenant_id = (select id from tenant order by id limit 1),
    br.code = coalesce(br.code, case when br.id = first_branch.first_id then 'MATRIZ' else concat('SUC-', br.id) end),
    br.phone = coalesce(br.phone, (select phone from business_settings order by id limit 1)),
    br.timezone = coalesce(br.timezone, (select timezone from business_settings order by id limit 1), 'America/Mexico_City')
where br.tenant_id is null;
update warehouse w
join (select branch_id, min(id) as first_id from warehouse group by branch_id) first_warehouse
    on first_warehouse.branch_id = w.branch_id
set w.tenant_id = (select id from tenant order by id limit 1),
    w.code = coalesce(w.code, case when w.id = first_warehouse.first_id then 'PRINCIPAL' else concat('ALM-', w.id) end)
where w.tenant_id is null;
update cash_register cr
join (select branch_id, min(id) as first_id from cash_register group by branch_id) first_register
    on first_register.branch_id = cr.branch_id
set cr.tenant_id = (select id from tenant order by id limit 1),
    cr.code = coalesce(cr.code, case when cr.id = first_register.first_id then 'CAJA01' else concat('CAJA-', cr.id) end)
where cr.tenant_id is null;
update category set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update supplier set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update customer set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update product set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update purchase set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update sale set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update inventory_movement set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update cash_register_session set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update cash_movement set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update expense_category set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update expense set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update business_settings set tenant_id = (select id from tenant order by id limit 1), business_id = (select id from business order by id limit 1) where tenant_id is null;
update capital_movement set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;
update inventory_stock s join product p on p.id = s.product_id set s.tenant_id = p.tenant_id where s.tenant_id is null;
update audit_log set tenant_id = (select id from tenant order by id limit 1) where tenant_id is null;

alter table app_user modify tenant_id bigint not null;
alter table business modify tenant_id bigint not null, modify legal_name varchar(200) null;
alter table branch modify tenant_id bigint not null, modify code varchar(60) not null;
alter table warehouse modify tenant_id bigint not null, modify code varchar(60) not null;
alter table cash_register modify tenant_id bigint not null, modify code varchar(60) not null;
alter table category modify tenant_id bigint not null;
alter table supplier modify tenant_id bigint not null;
alter table customer modify tenant_id bigint not null;
alter table product modify tenant_id bigint not null;
alter table purchase modify tenant_id bigint not null;
alter table sale modify tenant_id bigint not null;
alter table inventory_movement modify tenant_id bigint not null;
alter table cash_register_session modify tenant_id bigint not null;
alter table cash_movement modify tenant_id bigint not null;
alter table expense_category modify tenant_id bigint not null;
alter table expense modify tenant_id bigint not null;
alter table business_settings modify tenant_id bigint not null;
alter table capital_movement modify tenant_id bigint not null;
alter table inventory_stock modify tenant_id bigint not null;

-- Replace each unique index in the same ALTER: MySQL DDL commits per statement.
alter table product
    drop index uk_product_code,
    drop index uk_product_barcode,
    add unique key uk_product_tenant_code (tenant_id, code),
    add unique key uk_product_tenant_barcode (tenant_id, barcode),
    add key idx_product_tenant_name (tenant_id, name),
    add key idx_product_tenant_active (tenant_id, active),
    add constraint fk_product_tenant foreign key (tenant_id) references tenant(id);

alter table category
    drop index uk_category_name,
    add unique key uk_category_tenant_name (tenant_id, name),
    add key idx_category_tenant_active (tenant_id, active),
    add constraint fk_category_tenant foreign key (tenant_id) references tenant(id);

alter table expense_category
    drop index uk_expense_category_name,
    add unique key uk_expense_category_tenant_name (tenant_id, name),
    add key idx_expense_category_tenant_active (tenant_id, active),
    add constraint fk_expense_category_tenant foreign key (tenant_id) references tenant(id);

alter table app_user
    add key idx_app_user_tenant_username (tenant_id, username),
    add constraint fk_app_user_tenant foreign key (tenant_id) references tenant(id);

alter table business
    add key idx_business_tenant_active (tenant_id, active),
    add constraint fk_business_tenant foreign key (tenant_id) references tenant(id);

alter table branch
    add unique key uk_branch_tenant_business_code (tenant_id, business_id, code),
    add key idx_branch_tenant_active (tenant_id, active),
    add constraint fk_branch_tenant foreign key (tenant_id) references tenant(id);

alter table warehouse
    add unique key uk_warehouse_tenant_branch_code (tenant_id, branch_id, code),
    add key idx_warehouse_tenant_active (tenant_id, active),
    add constraint fk_warehouse_tenant foreign key (tenant_id) references tenant(id);

alter table cash_register
    add unique key uk_cash_register_tenant_branch_code (tenant_id, branch_id, code),
    add key idx_cash_register_tenant_active (tenant_id, active),
    add constraint fk_cash_register_tenant foreign key (tenant_id) references tenant(id);

alter table supplier
    add key idx_supplier_tenant_active (tenant_id, active),
    add key idx_supplier_tenant_name (tenant_id, name),
    add constraint fk_supplier_tenant foreign key (tenant_id) references tenant(id);

alter table customer
    add key idx_customer_tenant_active (tenant_id, active),
    add key idx_customer_tenant_name (tenant_id, name),
    add constraint fk_customer_tenant foreign key (tenant_id) references tenant(id);

alter table purchase
    add key idx_purchase_tenant_date (tenant_id, purchase_date),
    add constraint fk_purchase_tenant foreign key (tenant_id) references tenant(id);

alter table sale
    add key idx_sale_tenant_date (tenant_id, sale_date),
    add key idx_sale_tenant_cashier_date (tenant_id, cashier_id, sale_date),
    add constraint fk_sale_tenant foreign key (tenant_id) references tenant(id);

alter table inventory_movement
    add key idx_inventory_movement_tenant_product_created (tenant_id, product_id, created_at),
    add constraint fk_inventory_movement_tenant foreign key (tenant_id) references tenant(id);

alter table cash_register_session
    add key idx_cash_session_tenant_cashier_open (tenant_id, cashier_id, open),
    add constraint fk_cash_session_tenant foreign key (tenant_id) references tenant(id);

alter table cash_movement
    add key idx_cash_movement_tenant_session (tenant_id, cash_register_session_id),
    add constraint fk_cash_movement_tenant foreign key (tenant_id) references tenant(id);

alter table expense
    add key idx_expense_tenant_date (tenant_id, expense_date),
    add constraint fk_expense_tenant foreign key (tenant_id) references tenant(id);

alter table business_settings
    add key idx_business_settings_tenant (tenant_id),
    add constraint fk_business_settings_tenant foreign key (tenant_id) references tenant(id),
    add constraint fk_business_settings_business foreign key (business_id) references business(id);

alter table capital_movement
    add key idx_capital_movement_tenant_type_date (tenant_id, type, movement_date),
    add constraint fk_capital_movement_tenant foreign key (tenant_id) references tenant(id);

-- The tenant-leading unique key cannot support the existing product_id foreign key.
alter table inventory_stock add key idx_inventory_stock_product (product_id);

alter table inventory_stock
    drop index uk_inventory_stock_product_warehouse,
    add unique key uk_inventory_stock_tenant_product_warehouse (tenant_id, product_id, warehouse_id),
    add key idx_inventory_stock_tenant_warehouse (tenant_id, warehouse_id),
    add constraint fk_inventory_stock_tenant foreign key (tenant_id) references tenant(id);

alter table audit_log
    add key idx_audit_log_tenant_created (tenant_id, created_at),
    add constraint fk_audit_log_tenant foreign key (tenant_id) references tenant(id);
