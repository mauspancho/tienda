alter table customer add column last_name varchar(255) null after name;

alter table sale
    add column settlement_type enum('IMMEDIATE','CREDIT') not null default 'IMMEDIATE' after status,
    add column cancelled_at datetime(6) null,
    add column cancelled_by varchar(255) null,
    add column cancellation_reason varchar(255) null;

create index idx_sale_credit_customer_date on sale(customer_id, settlement_type, status, sale_date);

create table credit_payment (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    customer_id bigint not null,
    method enum('CASH','CARD','TRANSFER') not null,
    amount decimal(14,2) not null,
    notes varchar(255),
    status enum('ACTIVE','REVERSED') not null default 'ACTIVE',
    user_id bigint not null,
    reversed_at datetime(6),
    reversed_by varchar(255),
    reversal_reason varchar(255),
    primary key (id),
    key idx_credit_payment_customer_date (customer_id, created_at),
    constraint fk_credit_payment_customer foreign key (customer_id) references customer(id),
    constraint fk_credit_payment_user foreign key (user_id) references app_user(id),
    constraint chk_credit_payment_amount check (amount > 0)
) engine=InnoDB default charset=utf8mb4;

create table credit_payment_application (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    credit_payment_id bigint not null,
    sale_id bigint not null,
    amount decimal(14,2) not null,
    primary key (id),
    unique key uk_credit_payment_application (credit_payment_id, sale_id),
    key idx_credit_application_sale (sale_id),
    constraint fk_credit_application_payment foreign key (credit_payment_id) references credit_payment(id),
    constraint fk_credit_application_sale foreign key (sale_id) references sale(id),
    constraint chk_credit_application_amount check (amount > 0)
) engine=InnoDB default charset=utf8mb4;

alter table cash_movement modify column type enum(
    'OPENING','SALE','EXPENSE','WITHDRAWAL','DEPOSIT','REFUND','CLOSING',
    'CREDIT_PAYMENT','CREDIT_PAYMENT_REVERSAL'
) not null;
