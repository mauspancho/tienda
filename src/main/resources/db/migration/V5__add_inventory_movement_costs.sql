alter table inventory_movement
    add column unit_cost decimal(14,2) null,
    add column previous_purchase_cost decimal(14,2) null,
    add column new_purchase_cost decimal(14,2) null,
    add column cost_adjustment decimal(14,2) null;
