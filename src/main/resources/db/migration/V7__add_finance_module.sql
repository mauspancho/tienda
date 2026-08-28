create table capital_movement (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    movement_date date not null,
    type enum('INITIAL_INVESTMENT','OWNER_CONTRIBUTION','REINVESTMENT','OWNER_WITHDRAWAL','CAPITAL_ADJUSTMENT') not null,
    amount decimal(14,2) not null,
    description varchar(500),
    purchase_id bigint,
    primary key (id),
    key idx_capital_movement_date (movement_date),
    key idx_capital_movement_type_date (type, movement_date)
) engine=InnoDB default charset=utf8mb4;

alter table purchase
    add column funding_source enum('BUSINESS_CASH','OWNER_CAPITAL','SUPPLIER_CREDIT','OTHER','UNKNOWN') not null default 'UNKNOWN';

create index idx_purchase_date_funding_source on purchase (purchase_date, funding_source);
create index idx_sale_sale_date_status on sale (sale_date, status);
