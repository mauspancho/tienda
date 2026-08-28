alter table inventory_movement
    add column reversed bit not null default 0,
    add column reversed_at datetime(6) null,
    add column reversed_by varchar(255) null,
    add column reversal_movement_id bigint null;
