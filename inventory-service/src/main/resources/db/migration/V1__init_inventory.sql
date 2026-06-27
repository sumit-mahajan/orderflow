-- Inventory schema (created in the service's own schema via search_path).

create table inventory_item (
    sku           varchar(64)  primary key,
    available_qty integer      not null check (available_qty >= 0),
    reserved_qty  integer      not null default 0 check (reserved_qty >= 0),
    version       integer      not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create table inventory_reservation (
    id         uuid         primary key,
    order_id   uuid         not null,
    sku        varchar(64)  not null,
    qty        integer      not null check (qty > 0),
    status     varchar(16)  not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint uq_reservation unique (order_id, sku)
);

-- Demo stock. SKU-LIMITED is intentionally scarce for the concurrent-orders demo.
insert into inventory_item (sku, available_qty) values
    ('SKU-LAPTOP', 10),
    ('SKU-PHONE', 5),
    ('SKU-LIMITED', 3);
