create table order_projection(order_id uuid primary key, aggregate_version bigint not null, status text not null,
 total_amount numeric(14,2) not null, currency text not null, created_at timestamptz not null,
 event_at timestamptz not null, projected_at timestamptz not null default clock_timestamp());
create index order_projection_month on order_projection(created_at) where status='CONFIRMED';
