insert into catalog_product(id,name,description,price,currency) values
('10000000-0000-0000-0000-000000000001','Architecture field notebook','Capture assumptions, diagrams and the decisions behind your next system.','499.00','INR'),
('10000000-0000-0000-0000-000000000002','Focus desk lamp','A warm, adjustable light for deep work and thoughtful design.','2499.50','INR'),
('10000000-0000-0000-0000-000000000003','Everyday travel mug','Keep the coffee warm while the architecture discussion gets interesting.','899.00','INR');
insert into inventory_stock(product_id,available) select id,20 from catalog_product;
