-- Payment schema (service's own schema via search_path).

create table payment (
    id             uuid           primary key,
    order_id       uuid           not null unique,
    amount         numeric(12,2)  not null check (amount > 0),
    status         varchar(16)    not null,
    failure_reason varchar(128)   null,
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now()
);
