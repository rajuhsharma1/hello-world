-- Legacy email values remain private in storage; new APIs and events use contact_number only.
alter table appointment alter column customer_email drop not null;
alter table appointment add column contact_number varchar(25) not null default 'Not recorded';
alter table appointment alter column contact_number drop default;
