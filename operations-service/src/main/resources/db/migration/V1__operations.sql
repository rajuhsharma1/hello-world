create table staff (
 id uuid primary key, name varchar(100) not null, role varchar(100) not null
);
create table attendance (
 id uuid primary key, staff_id uuid not null references staff(id), day date not null,
 status varchar(10) not null check (status in ('PRESENT','ABSENT','LEAVE')),
 check_in time, check_out time,
 unique(staff_id,day),
 check ((status='PRESENT' and check_in is not null and (check_out is null or check_out>check_in))
     or (status in ('ABSENT','LEAVE') and check_in is null and check_out is null))
);
create index attendance_day_idx on attendance(day);
create table sale (
 id uuid primary key, day date not null, customer_name varchar(100) not null,
 contact_number varchar(25) not null, treatment varchar(100) not null,
 staff_id uuid not null references staff(id), amount numeric(10,2) not null check(amount>0),
 payment_method varchar(4) not null check(payment_method in ('UPI','CASH','CARD')),
 created_at timestamptz not null
);
create index sale_day_created_idx on sale(day,created_at);
