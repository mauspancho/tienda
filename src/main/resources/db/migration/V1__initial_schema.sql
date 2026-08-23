create table role (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(60) not null,
    description varchar(160),
    primary key (id),
    unique key uk_role_name (name)
) engine=InnoDB default charset=utf8mb4;

create table app_user (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    username varchar(60) not null,
    password_hash varchar(255) not null,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    email varchar(255),
    active bit not null,
    last_login datetime(6),
    primary key (id),
    unique key uk_app_user_username (username)
) engine=InnoDB default charset=utf8mb4;

create table user_roles (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id),
    constraint fk_user_roles_user foreign key (user_id) references app_user(id),
    constraint fk_user_roles_role foreign key (role_id) references role(id)
) engine=InnoDB default charset=utf8mb4;

create table category (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(120) not null,
    description varchar(255),
    active bit not null,
    primary key (id),
    unique key uk_category_name (name)
) engine=InnoDB default charset=utf8mb4;

create table supplier (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(255),
    company_name varchar(255),
    phone varchar(255),
    email varchar(255),
    address varchar(255),
    tax_id varchar(255),
    notes varchar(255),
    active bit not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

create table customer (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(255) not null,
    phone varchar(255),
    email varchar(255),
    address varchar(255),
    tax_id varchar(255),
    notes varchar(255),
    active bit not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

create table product (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    code varchar(60) not null,
    barcode varchar(80),
    name varchar(255) not null,
    description varchar(255),
    category_id bigint,
    purchase_cost decimal(14,2) not null,
    sale_price decimal(14,2) not null,
    current_stock decimal(14,3) not null,
    minimum_stock decimal(14,3) not null,
    unit enum('PIEZA','KG','GRAMO','LITRO','ML','PAQUETE','CAJA') not null,
    supplier_id bigint,
    tax decimal(8,2),
    active bit not null,
    primary key (id),
    unique key uk_product_code (code),
    unique key uk_product_barcode (barcode),
    key idx_product_name (name),
    constraint fk_product_category foreign key (category_id) references category(id),
    constraint fk_product_supplier foreign key (supplier_id) references supplier(id)
) engine=InnoDB default charset=utf8mb4;

create table purchase (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    supplier_id bigint,
    purchase_date date not null,
    external_folio varchar(255),
    status enum('DRAFT','CONFIRMED','CANCELLED') not null,
    subtotal decimal(14,2) not null,
    tax decimal(14,2) not null,
    total decimal(14,2) not null,
    notes varchar(255),
    user_id bigint,
    primary key (id),
    key idx_purchase_created_at (created_at),
    constraint fk_purchase_supplier foreign key (supplier_id) references supplier(id),
    constraint fk_purchase_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

create table purchase_item (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    purchase_id bigint not null,
    product_id bigint not null,
    quantity decimal(14,3) not null,
    unit_cost decimal(14,2) not null,
    subtotal decimal(14,2) not null,
    primary key (id),
    constraint fk_purchase_item_purchase foreign key (purchase_id) references purchase(id),
    constraint fk_purchase_item_product foreign key (product_id) references product(id)
) engine=InnoDB default charset=utf8mb4;

create table sale (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    folio varchar(40) not null,
    sale_date datetime(6) not null,
    cashier_id bigint,
    customer_id bigint,
    subtotal decimal(14,2) not null,
    discount decimal(14,2) not null,
    tax decimal(14,2) not null,
    total decimal(14,2) not null,
    status enum('COMPLETED','CANCELLED','RETURNED') not null,
    primary key (id),
    unique key uk_sale_folio (folio),
    key idx_sale_created_at (created_at),
    key idx_sale_sale_date (sale_date),
    constraint fk_sale_cashier foreign key (cashier_id) references app_user(id),
    constraint fk_sale_customer foreign key (customer_id) references customer(id)
) engine=InnoDB default charset=utf8mb4;

create table sale_item (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    sale_id bigint not null,
    product_id bigint not null,
    product_name_snapshot varchar(255) not null,
    quantity decimal(14,3) not null,
    unit_price decimal(14,2) not null,
    unit_cost decimal(14,2) not null,
    discount decimal(14,2) not null,
    subtotal decimal(14,2) not null,
    profit decimal(14,2) not null,
    primary key (id),
    constraint fk_sale_item_sale foreign key (sale_id) references sale(id),
    constraint fk_sale_item_product foreign key (product_id) references product(id)
) engine=InnoDB default charset=utf8mb4;

create table payment (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    sale_id bigint not null,
    method enum('CASH','CARD','TRANSFER') not null,
    amount decimal(14,2) not null,
    received_amount decimal(14,2) not null,
    change_amount decimal(14,2) not null,
    primary key (id),
    unique key uk_payment_sale (sale_id),
    constraint fk_payment_sale foreign key (sale_id) references sale(id)
) engine=InnoDB default charset=utf8mb4;

create table inventory_movement (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    product_id bigint not null,
    movement_type enum('PURCHASE','SALE','SALE_RETURN','PURCHASE_RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT','INITIAL_STOCK') not null,
    quantity decimal(14,3) not null,
    previous_stock decimal(14,3) not null,
    new_stock decimal(14,3) not null,
    reference_type varchar(255),
    reference_id bigint,
    notes varchar(255),
    user_id bigint,
    primary key (id),
    key idx_inventory_product (product_id),
    key idx_inventory_created_at (created_at),
    constraint fk_inventory_product foreign key (product_id) references product(id),
    constraint fk_inventory_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

create table cash_register_session (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    cashier_id bigint,
    opened_at datetime(6) not null,
    closed_at datetime(6),
    opening_amount decimal(14,2) not null,
    expected_amount decimal(14,2),
    counted_amount decimal(14,2),
    difference_amount decimal(14,2),
    open bit not null,
    primary key (id),
    constraint fk_cash_session_cashier foreign key (cashier_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

create table cash_movement (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    cash_register_session_id bigint,
    type enum('OPENING','SALE','EXPENSE','WITHDRAWAL','DEPOSIT','REFUND','CLOSING') not null,
    amount decimal(14,2) not null,
    reference_type varchar(255),
    reference_id bigint,
    notes varchar(255),
    user_id bigint,
    primary key (id),
    constraint fk_cash_movement_session foreign key (cash_register_session_id) references cash_register_session(id),
    constraint fk_cash_movement_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

create table expense_category (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(255) not null,
    active bit not null,
    primary key (id),
    unique key uk_expense_category_name (name)
) engine=InnoDB default charset=utf8mb4;

create table expense (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    concept varchar(255) not null,
    category_id bigint,
    amount decimal(14,2) not null,
    expense_date date not null,
    notes varchar(255),
    cash_register_session_id bigint,
    user_id bigint,
    primary key (id),
    constraint fk_expense_category foreign key (category_id) references expense_category(id),
    constraint fk_expense_session foreign key (cash_register_session_id) references cash_register_session(id),
    constraint fk_expense_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

create table business_settings (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    store_name varchar(255) not null,
    address varchar(255),
    phone varchar(255),
    tax_id varchar(255),
    currency varchar(255),
    currency_symbol varchar(255),
    timezone varchar(255),
    default_tax decimal(8,2),
    logo_path varchar(255),
    negative_stock_allowed bit not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

create table audit_log (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    user_id bigint,
    action varchar(255),
    entity_type varchar(255),
    entity_id bigint,
    details varchar(255),
    ip_address varchar(255),
    primary key (id),
    constraint fk_audit_user foreign key (user_id) references app_user(id)
) engine=InnoDB default charset=utf8mb4;

insert into role(id, version, created_at, updated_at, name, description) values
(1, 0, current_timestamp, current_timestamp, 'ROLE_ADMIN', 'Administrador'),
(2, 0, current_timestamp, current_timestamp, 'ROLE_CAJERO', 'Cajero');

insert into category(version, created_at, updated_at, name, description, active) values
(0, current_timestamp, current_timestamp, 'Refrescos', null, true),
(0, current_timestamp, current_timestamp, 'Botanas', null, true),
(0, current_timestamp, current_timestamp, 'Pan', null, true),
(0, current_timestamp, current_timestamp, 'Lácteos', null, true),
(0, current_timestamp, current_timestamp, 'Abarrotes', null, true),
(0, current_timestamp, current_timestamp, 'Limpieza', null, true),
(0, current_timestamp, current_timestamp, 'Higiene', null, true),
(0, current_timestamp, current_timestamp, 'Dulces', null, true),
(0, current_timestamp, current_timestamp, 'Otros', null, true);

insert into expense_category(version, created_at, updated_at, name, active) values
(0, current_timestamp, current_timestamp, 'Servicios', true),
(0, current_timestamp, current_timestamp, 'Renta', true),
(0, current_timestamp, current_timestamp, 'Insumos', true),
(0, current_timestamp, current_timestamp, 'Transporte', true),
(0, current_timestamp, current_timestamp, 'Mantenimiento', true),
(0, current_timestamp, current_timestamp, 'Otros', true);

insert into customer(id, version, created_at, updated_at, name, active) values
(1, 0, current_timestamp, current_timestamp, 'Público General', true);

insert into business_settings(id, version, created_at, updated_at, store_name, currency, currency_symbol, timezone, default_tax, negative_stock_allowed)
values (1, 0, current_timestamp, current_timestamp, 'Mi tienda', 'MXN', '$', 'America/Mexico_City', 0.00, false);
