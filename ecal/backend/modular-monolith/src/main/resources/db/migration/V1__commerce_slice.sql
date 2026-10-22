-- One database is an explicit Phase 1 choice: checkout can commit all effects atomically.
-- Prices are exact numeric snapshots, never client-provided floating point values.
create table catalog_product (
 id uuid primary key, name varchar(120) not null, description varchar(500) not null,
 price numeric(12,2) not null check(price>0), currency char(3) not null check(currency='INR')
);
create table inventory_stock (
 product_id uuid primary key references catalog_product(id), available integer not null check(available>=0)
);
create table purchase_order (
 id uuid primary key, buyer_id uuid not null, idempotency_key uuid not null,
 product_id uuid not null references catalog_product(id), product_name varchar(120) not null,
 quantity integer not null check(quantity between 1 and 10), unit_price numeric(12,2) not null,
 total_amount numeric(14,2) not null, currency char(3) not null,
 requested_payment varchar(10) not null check(requested_payment in ('APPROVE','DECLINE')),
 payment_status varchar(12) check(payment_status in ('AUTHORIZED','DECLINED')),
 status varchar(12) not null check(status in ('PENDING','RESERVED','CONFIRMED','CANCELLED')),
 created_at timestamptz not null,
 unique(buyer_id,idempotency_key), check(total_amount=unit_price*quantity),
 check((status in ('PENDING','RESERVED') and payment_status is null)
    or (status='CONFIRMED' and payment_status='AUTHORIZED')
    or (status='CANCELLED' and payment_status='DECLINED'))
);
create index order_buyer_recent on purchase_order(buyer_id,created_at desc,id desc);
create table simulated_payment (
 order_id uuid primary key references purchase_order(id), amount numeric(14,2) not null,
 status varchar(12) not null check(status in ('AUTHORIZED','DECLINED'))
);
create table notification_record (
 order_id uuid primary key references purchase_order(id), message varchar(240) not null
);
