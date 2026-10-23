create table catalog_product(id uuid primary key,name varchar(120) not null,description varchar(500) not null,price numeric(12,2) not null check(price>0),currency char(3) not null check(currency='INR'));
create table purchase_order(
 id uuid primary key,buyer_id uuid not null,idempotency_key uuid not null,product_id uuid not null references catalog_product(id),product_name varchar(120) not null,quantity integer not null check(quantity between 1 and 10),unit_price numeric(12,2) not null,total_amount numeric(14,2) not null,currency char(3) not null check(currency='INR'),requested_payment varchar(8) not null check(requested_payment in ('APPROVE','DECLINE')),
 status varchar(20) not null check(status in ('PENDING','RESERVED','PAYMENT_UNKNOWN','RELEASE_PENDING','CONFIRMED','CANCELLED','REJECTED')),
 payment_status varchar(12) check(payment_status in ('AUTHORIZED','DECLINED')),
 created_at timestamptz not null default now(),lease_token uuid,lease_until timestamptz,
 unique(buyer_id,idempotency_key),check(total_amount=unit_price*quantity),
 check((status='CONFIRMED' and payment_status is not null and payment_status='AUTHORIZED') or (status in ('CANCELLED','RELEASE_PENDING') and payment_status is not null and payment_status='DECLINED') or (status in ('PENDING','RESERVED','PAYMENT_UNKNOWN','REJECTED') and payment_status is null))
);
create index buyer_recent on purchase_order(buyer_id,created_at desc,id desc);
create index buyer_status_recent on purchase_order(buyer_id,status,created_at desc,id desc);
create index catalog_name_id on catalog_product(name,id);
create table notification_record(order_id uuid primary key references purchase_order(id),message varchar(240) not null);
create table migration_receipt(id integer primary key check(id=1),snapshot_sha char(64) not null,completed_at timestamptz not null default now());
insert into catalog_product(id,name,description,price,currency) values
('10000000-0000-0000-0000-000000000001','Architecture field notebook','Capture assumptions, diagrams and the decisions behind your next system.','499.00','INR'),
('10000000-0000-0000-0000-000000000002','Focus desk lamp','A warm, adjustable light for deep work and thoughtful design.','2499.50','INR'),
('10000000-0000-0000-0000-000000000003','Everyday travel mug','Keep the coffee warm while the architecture discussion gets interesting.','899.00','INR');
