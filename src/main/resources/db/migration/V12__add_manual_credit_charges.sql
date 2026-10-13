create table credit_charge (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    customer_id bigint not null,
    amount decimal(14,2) not null,
    concept varchar(255) not null,
    status enum('ACTIVE','REVERSED') not null default 'ACTIVE',
    user_id bigint not null,
    reversed_at datetime(6),
    reversed_by varchar(255),
    reversal_reason varchar(255),
    primary key (id),
    key idx_credit_charge_customer_date (customer_id, created_at),
    constraint fk_credit_charge_customer foreign key (customer_id) references customer(id),
    constraint fk_credit_charge_user foreign key (user_id) references app_user(id),
    constraint chk_credit_charge_amount check (amount > 0)
) engine=InnoDB default charset=utf8mb4;

alter table credit_payment_application
    drop index uk_credit_payment_application,
    modify column sale_id bigint null,
    add column credit_charge_id bigint null after sale_id,
    add key idx_credit_application_charge (credit_charge_id),
    add unique key uk_credit_application_sale (credit_payment_id, sale_id),
    add unique key uk_credit_application_charge (credit_payment_id, credit_charge_id),
    add constraint fk_credit_application_charge foreign key (credit_charge_id) references credit_charge(id),
    add constraint chk_credit_application_target check (
        (sale_id is not null and credit_charge_id is null)
        or (sale_id is null and credit_charge_id is not null)
    );
