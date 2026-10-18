create table ark_user (
 id uuid primary key, username varchar(50) not null unique, display_name varchar(100) not null,
 password_hash varchar(100) not null, role varchar(5) not null check(role in ('OWNER','ADMIN','STAFF')),
 enabled boolean not null default true, must_change boolean not null default false,
 failed_attempts integer not null default 0, locked_until timestamptz, created_at timestamptz not null default now()
);
create table ark_session (
 token_hash varchar(64) primary key, user_id uuid references ark_user(id),
 created_at timestamptz not null default now(), last_seen timestamptz not null default now()
);
create index ark_session_user_idx on ark_session(user_id);
create table security_audit (
 id uuid primary key, occurred_at timestamptz not null default now(), actor varchar(50) not null,
 action varchar(50) not null, target varchar(100) not null
);
create index security_audit_time_idx on security_audit(occurred_at);
