-- Shipment schema (service's own schema via search_path).

create table shipment (
    id             uuid           primary key,
    order_id       uuid           not null unique,
    carrier_ref    varchar(64)    null,
    status         varchar(16)    not null,
    failure_reason varchar(128)   null,
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now()
);
