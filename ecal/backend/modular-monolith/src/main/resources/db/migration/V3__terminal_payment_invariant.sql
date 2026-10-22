-- PostgreSQL CHECK accepts UNKNOWN: a status comparison alone does not reject NULL.
-- Add an explicit presence guard without rewriting an already-applied migration.
alter table purchase_order add constraint terminal_order_has_payment
 check(status in ('PENDING','RESERVED') or payment_status is not null);
