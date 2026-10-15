alter table whatsapp_manual_promotion_delivery
    add column recipient_type varchar(16) not null default 'GROUP' after group_id,
    add column recipient_address varchar(100) null after recipient_type;

update whatsapp_manual_promotion_delivery d
join whatsapp_group g on g.id = d.group_id
set d.recipient_address = g.whatsapp_id;

alter table whatsapp_manual_promotion_delivery
    modify column recipient_address varchar(100) not null,
    modify column group_id bigint null,
    drop index uk_whatsapp_manual_promotion_delivery_group,
    add unique key uk_whatsapp_manual_promotion_delivery_recipient
        (promotion_id, recipient_type, recipient_address);
