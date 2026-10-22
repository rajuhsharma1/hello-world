-- Preserve original import identity independently of stock, which checkouts legitimately change.
create table catalog_import_product (
 product_id uuid primary key references catalog_product(id),
 fingerprint char(64) not null,
 created_at timestamptz not null default current_timestamp
);
