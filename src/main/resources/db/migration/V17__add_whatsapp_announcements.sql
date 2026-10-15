alter table whatsapp_group
    add column selected_for_announcements bit not null default 0 after available,
    add key idx_whatsapp_group_tenant_announcements (tenant_id, selected_for_announcements, available);

create table tenant_whatsapp_announcement_settings (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    tenant_id bigint not null,
    enabled bit not null default 0,
    confirmed_connection_id bigint,
    confirmed_provider_id bigint,
    confirmed_phone_number varchar(40),
    confirmed_at datetime(6),
    primary key (id),
    unique key uk_tenant_whatsapp_announcement_settings_tenant (tenant_id),
    constraint fk_tenant_whatsapp_announcement_settings_tenant foreign key (tenant_id) references tenant(id)
) engine=InnoDB default charset=utf8mb4;

create table platform_automation_token (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    name varchar(120) not null,
    token_prefix varchar(24) not null,
    token_hash varchar(64) not null,
    scopes varchar(255) not null,
    active bit not null default 1,
    last_used_at datetime(6),
    revoked_at datetime(6),
    expires_at datetime(6),
    primary key (id),
    unique key uk_platform_automation_token_hash (token_hash),
    key idx_platform_automation_token_active (active)
) engine=InnoDB default charset=utf8mb4;

create table platform_automation_audit (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    token_id bigint not null,
    action varchar(120) not null,
    tenant_code varchar(60),
    http_status integer not null,
    remote_address varchar(80),
    primary key (id),
    key idx_platform_automation_audit_token_created (token_id, created_at),
    constraint fk_platform_automation_audit_token foreign key (token_id) references platform_automation_token(id)
) engine=InnoDB default charset=utf8mb4;

create table platform_automation_idempotency (
    id bigint not null auto_increment,
    version bigint,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    created_by varchar(255),
    updated_by varchar(255),
    token_id bigint not null,
    tenant_id bigint not null,
    operation_name varchar(80) not null,
    key_hash varchar(64) not null,
    request_hash varchar(64) not null,
    response_json longtext not null,
    primary key (id),
    unique key uk_platform_automation_idempotency (token_id, tenant_id, operation_name, key_hash),
    key idx_platform_automation_idempotency_tenant (tenant_id),
    constraint fk_platform_automation_idempotency_token foreign key (token_id) references platform_automation_token(id),
    constraint fk_platform_automation_idempotency_tenant foreign key (tenant_id) references tenant(id)
) engine=InnoDB default charset=utf8mb4;
