alter table staff add column contact_number varchar(16);
alter table staff add column address varchar(1000);
alter table staff add column joined_on date;
create table staff_contract (
 id uuid primary key, staff_id uuid not null references staff(id), staff_name varchar(100) not null,
 staff_role varchar(100) not null, details jsonb not null, created_at timestamptz not null
);
create index contract_staff_idx on staff_contract(staff_id,created_at);
create table salary_structure (
 id uuid primary key, staff_id uuid not null references staff(id), staff_name varchar(100) not null,
 effective_from date not null, annual_ctc numeric(14,2) not null,
 monthly_basic numeric(12,2) not null, monthly_hra numeric(12,2) not null,
 monthly_allowances numeric(12,2) not null, monthly_employer_contributions numeric(12,2) not null,
 monthly_employee_deductions numeric(12,2) not null, annual_bonus numeric(12,2) not null,
 notes varchar(4000) not null, created_at timestamptz not null,
 check(annual_ctc>=0 and monthly_basic>=0 and monthly_hra>=0 and monthly_allowances>=0 and monthly_employer_contributions>=0 and monthly_employee_deductions>=0 and annual_bonus>=0)
);
create index salary_staff_idx on salary_structure(staff_id,effective_from);
create table expense (
 id uuid primary key, day date not null, category varchar(30) not null, description varchar(500) not null,
 amount numeric(10,2) not null check(amount>0), payment_method varchar(4) not null check(payment_method in ('UPI','CASH','CARD')),
 created_at timestamptz not null
);
create index expense_day_idx on expense(day,created_at);
create table emergency_contact (
 id uuid primary key, name varchar(100) not null, service varchar(100) not null,
 phone varchar(16) not null, secondary_phone varchar(16), address varchar(1000) not null,
 notes varchar(2000) not null
);
