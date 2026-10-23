create table payment(order_id uuid primary key,amount numeric(14,2) not null check(amount>0),currency char(3) not null check(currency='INR'),outcome varchar(8) not null check(outcome in ('APPROVE','DECLINE')),status varchar(12) not null check(status in ('AUTHORIZED','DECLINED')),
check((outcome='APPROVE' and status='AUTHORIZED') or (outcome='DECLINE' and status='DECLINED')));
create table migration_receipt(id integer primary key check(id=1),snapshot_sha char(64) not null,completed_at timestamptz not null default now());
