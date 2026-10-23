create function inventory_event() returns trigger language plpgsql as $$
begin
 if (TG_OP='INSERT' and NEW.state in ('RESERVED','REJECTED','RELEASED')) or
    (TG_OP='UPDATE' and NEW.state is distinct from OLD.state and NEW.state in ('RESERVED','REJECTED','RELEASED')) then
  perform ecal_event('inventory',NEW.order_id,case when NEW.state='RELEASED' then 2 else 1 end,
   case NEW.state when 'RESERVED' then 'InventoryReserved' when 'REJECTED' then 'InventoryRejected' else 'InventoryReleased' end,
   jsonb_build_object('productId',NEW.product_id,'quantity',NEW.quantity,'state',NEW.state));
 end if;
 return NEW;
end $$;
create trigger reservation_events after insert or update on reservation for each row execute function inventory_event();
