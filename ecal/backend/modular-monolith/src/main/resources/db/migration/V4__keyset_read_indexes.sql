-- The existing buyer/recent index still serves unfiltered history. This index serves
-- selective status reads and covers the projection, trading storage/write work for fewer heap visits.
-- A lab migration takes normal DDL locks; a live large table would require a reviewed
-- CREATE INDEX CONCURRENTLY/nontransactional rollout, not a claim of zero downtime.
create index order_buyer_status_recent on purchase_order(buyer_id,status,created_at desc,id desc)
 include(product_id,product_name,quantity,unit_price,total_amount,currency,payment_status);
create index catalog_name_id on catalog_product(name,id);
