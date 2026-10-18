-- Legacy email values remain private in storage; new APIs and events use contact_number only.
alter table notification alter column customer_email drop not null;
alter table notification add column contact_number varchar(25) not null default 'Not recorded';
alter table notification alter column contact_number drop default;
