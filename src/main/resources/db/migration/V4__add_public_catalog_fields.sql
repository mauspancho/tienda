alter table product
    add column promoted bit not null default b'0',
    add column promotion_order integer null;

alter table business_settings
    add column catalog_enabled bit not null default b'1',
    add column catalog_title varchar(255),
    add column catalog_subtitle varchar(500),
    add column promotion_title varchar(255);

create index idx_product_catalog_active_category on product(active, category_id);
create index idx_product_promotion on product(promoted, promotion_order);
