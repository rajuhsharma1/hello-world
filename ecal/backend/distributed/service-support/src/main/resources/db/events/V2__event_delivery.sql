-- This additional location is opted into only by Phase 4. Phase 3 still runs V1 alone.
create table event_outbox (
 sequence bigserial primary key,
 event_id uuid not null unique default gen_random_uuid(),
 source text not null, aggregate_id uuid not null, aggregate_version bigint not null,
 event_type text not null, payload jsonb not null,
 occurred_at timestamptz not null default clock_timestamp(),
 correlation_id text, trace_id text,
 published_at timestamptz
);
create index event_outbox_pending on event_outbox(sequence) where published_at is null;
create table event_inbox(event_id uuid primary key, fingerprint text not null, received_at timestamptz not null default now());
create table event_quarantine(fingerprint text primary key, raw_event text not null, reason text not null, received_at timestamptz not null default now());
-- A trigger reads transaction-local context. Background processing copies this context from the event.
create function ecal_event(src text, aggregate uuid, ver bigint, kind text, body jsonb) returns void language sql as $$
 insert into event_outbox(source,aggregate_id,aggregate_version,event_type,payload,correlation_id,trace_id)
 values(src,aggregate,ver,kind,body,nullif(current_setting('ecal.correlation_id',true),''),nullif(current_setting('ecal.trace_id',true),''));
$$;
