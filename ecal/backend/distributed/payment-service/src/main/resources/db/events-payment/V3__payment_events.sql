create function payment_event() returns trigger language plpgsql as $$
begin
 perform ecal_event('payment',NEW.order_id,1,
  case NEW.status when 'AUTHORIZED' then 'PaymentAuthorized' else 'PaymentFailed' end,
  jsonb_build_object('amount',NEW.amount::text,'currency',NEW.currency,'status',NEW.status));
 return NEW;
end $$;
create trigger payment_events after insert on payment for each row execute function payment_event();
