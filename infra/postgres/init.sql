-- OrderFlow: one Postgres, four schemas, one DB user per service (D-04).
-- Each schema is OWNED by its service user and NO cross-schema grants are issued, so the
-- "zero cross-schema queries" rule is enforced by Postgres permissions, not just discipline.
--
-- search_path is set per user to its own schema, so services use UNQUOTED, UNQUALIFIED table
-- names (avoids the reserved-word pain of a schema literally named "order").
-- Dev passwords are local-only and intentionally simple; nothing here is a real secret.

-- order-service
CREATE USER order_user WITH PASSWORD 'order_pw';
CREATE SCHEMA IF NOT EXISTS "order" AUTHORIZATION order_user;
ALTER ROLE order_user SET search_path TO "order";

-- inventory-service
CREATE USER inventory_user WITH PASSWORD 'inventory_pw';
CREATE SCHEMA IF NOT EXISTS inventory AUTHORIZATION inventory_user;
ALTER ROLE inventory_user SET search_path TO inventory;

-- payment-service (M2)
CREATE USER payment_user WITH PASSWORD 'payment_pw';
CREATE SCHEMA IF NOT EXISTS payment AUTHORIZATION payment_user;
ALTER ROLE payment_user SET search_path TO payment;

-- shipment-service (M3)
CREATE USER shipment_user WITH PASSWORD 'shipment_pw';
CREATE SCHEMA IF NOT EXISTS shipment AUTHORIZATION shipment_user;
ALTER ROLE shipment_user SET search_path TO shipment;
