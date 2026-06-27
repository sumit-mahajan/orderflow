-- Order (orchestrator) schema, created in the service's own schema via search_path.

create table orders (
    order_id        uuid          primary key,
    customer_id     uuid          not null,
    total_amount    numeric(12,2) not null check (total_amount > 0),
    status          varchar(32)   not null,
    idempotency_key varchar(128)  not null,
    simulate        jsonb,
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    deleted_at      timestamptz,
    constraint uq_orders_idempotency unique (idempotency_key)
);

create table order_items (
    id         uuid          primary key,
    order_id   uuid          not null references orders (order_id),
    sku        varchar(64)   not null,
    qty        integer       not null check (qty > 0),
    unit_price numeric(12,2) not null check (unit_price >= 0)
);
create index ix_order_items_order on order_items (order_id);

create table saga_instance (
    saga_id       uuid        primary key,
    order_id      uuid        not null unique references orders (order_id),
    current_state varchar(32) not null,
    version       integer     not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index ix_saga_state on saga_instance (current_state);

create table saga_step (
    id             uuid        primary key,
    saga_id        uuid        not null references saga_instance (saga_id),
    step           varchar(32) not null,
    direction      varchar(16) not null,
    status         varchar(16) not null,
    attempt        integer     not null default 1,
    correlation_id uuid        not null,
    error          text,
    created_at     timestamptz not null default now()
);
create index ix_saga_step on saga_step (saga_id, created_at);

create table outbox (
    id             uuid         primary key,
    aggregate_type varchar(32)  not null,
    aggregate_id   uuid         not null,
    topic          varchar(128) not null,
    msg_key        varchar(128) not null,
    payload        jsonb        not null,
    headers        jsonb,
    status         varchar(16)  not null default 'PENDING',
    created_at     timestamptz  not null default now(),
    published_at   timestamptz
);
create index ix_outbox_poll on outbox (status, created_at);
