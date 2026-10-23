alter table purchase_order add column event_version bigint not null default 0;
create table saga_work(order_id uuid primary key references purchase_order(id), attempts int not null default 0, next_attempt_at timestamptz not null default now(), correlation_id text, trace_id text);
create index saga_work_due on saga_work(next_attempt_at);
create function orders_event() returns trigger language plpgsql as $$
begin
 if TG_OP='INSERT' then
  NEW.event_version := 1;
 elsif NEW.status is distinct from OLD.status then
  NEW.event_version := OLD.event_version+1;
 else return NEW;
 end if;
 return NEW;
end $$;
create trigger purchase_order_version before insert or update on purchase_order for each row execute function orders_event();
create function publish_order_event() returns trigger language plpgsql as $$
begin
 if TG_OP='UPDATE' and NEW.status is not distinct from OLD.status then return NEW; end if;
 perform ecal_event('orders',NEW.id,NEW.event_version,
  case when TG_OP='INSERT' then 'OrderCreated'
       when NEW.status='CONFIRMED' then 'OrderConfirmed'
       when NEW.status in ('CANCELLED','REJECTED') then 'OrderCancelled'
       else 'OrderProgress' end,
  jsonb_build_object('id',NEW.id,'productId',NEW.product_id,'quantity',NEW.quantity,
   'totalAmount',NEW.total_amount::text,'currency',NEW.currency,'status',NEW.status,'createdAt',NEW.created_at));
 return NEW;
end $$;
create trigger purchase_order_events after insert or update on purchase_order for each row execute function publish_order_event();
