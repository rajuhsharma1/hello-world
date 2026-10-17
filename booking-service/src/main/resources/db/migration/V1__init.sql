create table appointment (
 id uuid primary key, customer_name varchar(100) not null, customer_email varchar(254) not null,
 treatment varchar(100) not null, therapist varchar(100) not null, starts_at timestamptz not null,
 unique(therapist, starts_at));
create table outbox (id uuid primary key, payload text not null, traceparent varchar(255), sent boolean not null default false);
