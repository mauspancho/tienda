create table business (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(160) not null,
    tax_id varchar(80),
    active bit not null,
    primary key (id),
    key idx_business_active (active)
) engine=InnoDB default charset=utf8mb4;

create table branch (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    business_id bigint not null,
    name varchar(160) not null,
    address varchar(255),
    active bit not null,
    primary key (id),
    key idx_branch_business_active (business_id, active),
    constraint fk_branch_business foreign key (business_id) references business(id)
) engine=InnoDB default charset=utf8mb4;

create table warehouse (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    branch_id bigint not null,
    name varchar(160) not null,
    main_warehouse bit not null,
    active bit not null,
    primary key (id),
    key idx_warehouse_branch_active (branch_id, active),
    key idx_warehouse_main (main_warehouse, active),
    constraint fk_warehouse_branch foreign key (branch_id) references branch(id)
) engine=InnoDB default charset=utf8mb4;

create table cash_register (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    branch_id bigint not null,
    name varchar(120) not null,
    active bit not null,
    primary key (id),
    key idx_cash_register_branch_active (branch_id, active),
    constraint fk_cash_register_branch foreign key (branch_id) references branch(id)
) engine=InnoDB default charset=utf8mb4;

insert into business(version, created_at, updated_at, name, tax_id, active)
select 0, current_timestamp, current_timestamp, coalesce(nullif(store_name, ''), 'Tienda POS'), tax_id, true
from business_settings
where not exists (select 1 from business)
limit 1;

insert into business(version, created_at, updated_at, name, tax_id, active)
select 0, current_timestamp, current_timestamp, 'Tienda POS', null, true
where not exists (select 1 from business);

insert into branch(version, created_at, updated_at, business_id, name, address, active)
select 0, current_timestamp, current_timestamp, b.id, 'Sucursal principal', bs.address, true
from business b
left join business_settings bs on bs.id = 1
where not exists (select 1 from branch)
order by b.id
limit 1;

insert into warehouse(version, created_at, updated_at, branch_id, name, main_warehouse, active)
select 0, current_timestamp, current_timestamp, br.id, 'Almacén principal', true, true
from branch br
where not exists (select 1 from warehouse)
order by br.id
limit 1;

insert into cash_register(version, created_at, updated_at, branch_id, name, active)
select 0, current_timestamp, current_timestamp, br.id, 'Caja principal', true
from branch br
where not exists (select 1 from cash_register)
order by br.id
limit 1;

alter table inventory_movement
    add column branch_id bigint null,
    add column warehouse_id bigint null;

alter table purchase
    add column branch_id bigint null,
    add column warehouse_id bigint null;

alter table sale
    add column branch_id bigint null,
    add column cash_register_id bigint null;

alter table cash_register_session
    add column branch_id bigint null,
    add column cash_register_id bigint null;

update inventory_movement
set branch_id = (select id from branch order by id limit 1),
    warehouse_id = (select id from warehouse order by id limit 1)
where branch_id is null or warehouse_id is null;

update purchase
set branch_id = (select id from branch order by id limit 1),
    warehouse_id = (select id from warehouse order by id limit 1)
where branch_id is null or warehouse_id is null;

update sale
set branch_id = (select id from branch order by id limit 1),
    cash_register_id = (select id from cash_register order by id limit 1)
where branch_id is null or cash_register_id is null;

update cash_register_session
set branch_id = (select id from branch order by id limit 1),
    cash_register_id = (select id from cash_register order by id limit 1)
where branch_id is null or cash_register_id is null;

alter table inventory_movement
    modify branch_id bigint not null,
    modify warehouse_id bigint not null;

alter table purchase
    modify branch_id bigint not null,
    modify warehouse_id bigint not null;

alter table sale
    modify branch_id bigint not null,
    modify cash_register_id bigint not null;

alter table cash_register_session
    modify branch_id bigint not null,
    modify cash_register_id bigint not null;

create table inventory_stock (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    product_id bigint not null,
    warehouse_id bigint not null,
    quantity decimal(14,3) not null,
    minimum_stock decimal(14,3) not null,
    primary key (id),
    unique key uk_inventory_stock_product_warehouse (product_id, warehouse_id),
    key idx_inventory_stock_warehouse (warehouse_id),
    constraint fk_inventory_stock_product foreign key (product_id) references product(id),
    constraint fk_inventory_stock_warehouse foreign key (warehouse_id) references warehouse(id)
) engine=InnoDB default charset=utf8mb4;

insert into inventory_stock(version, created_at, updated_at, product_id, warehouse_id, quantity, minimum_stock)
select 0, current_timestamp, current_timestamp, p.id, w.id, p.current_stock, p.minimum_stock
from product p
join (select id from warehouse order by id limit 1) w on 1 = 1
where not exists (
    select 1
    from inventory_stock s
    where s.product_id = p.id and s.warehouse_id = w.id
);

create index idx_inventory_movement_branch_created on inventory_movement (branch_id, created_at);
create index idx_inventory_movement_warehouse_product on inventory_movement (warehouse_id, product_id);
create index idx_purchase_branch_date on purchase (branch_id, purchase_date);
create index idx_purchase_warehouse_date on purchase (warehouse_id, purchase_date);
create index idx_sale_branch_date on sale (branch_id, sale_date);
create index idx_cash_session_branch_open on cash_register_session (branch_id, open);
create index idx_cash_session_register_open on cash_register_session (cash_register_id, open);

alter table inventory_movement
    add constraint fk_inventory_movement_branch foreign key (branch_id) references branch(id),
    add constraint fk_inventory_movement_warehouse foreign key (warehouse_id) references warehouse(id);

alter table purchase
    add constraint fk_purchase_branch foreign key (branch_id) references branch(id),
    add constraint fk_purchase_warehouse foreign key (warehouse_id) references warehouse(id);

alter table sale
    add constraint fk_sale_branch foreign key (branch_id) references branch(id),
    add constraint fk_sale_cash_register foreign key (cash_register_id) references cash_register(id);

alter table cash_register_session
    add constraint fk_cash_session_branch foreign key (branch_id) references branch(id),
    add constraint fk_cash_session_cash_register foreign key (cash_register_id) references cash_register(id);