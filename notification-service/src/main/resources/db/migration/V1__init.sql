create table notification (id uuid primary key, appointment_id uuid not null unique, customer_email varchar(254) not null, message text not null, created_at timestamptz not null);
