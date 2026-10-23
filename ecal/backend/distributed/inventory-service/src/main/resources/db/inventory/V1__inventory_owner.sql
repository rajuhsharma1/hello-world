create table stock(product_id uuid primary key,available integer not null check(available>=0));
create table reservation(order_id uuid primary key,product_id uuid not null references stock(product_id),quantity integer not null check(quantity between 1 and 10),state varchar(12) not null check(state in ('PENDING','RESERVED','RELEASED','REJECTED')));
insert into stock(product_id,available) values ('10000000-0000-0000-0000-000000000001',20),('10000000-0000-0000-0000-000000000002',20),('10000000-0000-0000-0000-000000000003',20);
create table migration_receipt(id integer primary key check(id=1),snapshot_sha char(64) not null,completed_at timestamptz not null default now());
