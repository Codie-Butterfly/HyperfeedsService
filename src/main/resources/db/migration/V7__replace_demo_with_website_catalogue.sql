-- Product names and category images are sourced from the public Hyperfeeds website.
-- Pack sizes, prices and stock below remain test values until confirmed by the business.

INSERT INTO product_categories (id,name,description,active) VALUES
(gen_random_uuid(),'Rabbit Feed','Feed formulated for domestic and commercial rabbits.',TRUE),
(gen_random_uuid(),'Nutritional Blocks','Mineral and seasonal lick blocks for livestock and game.',TRUE),
(gen_random_uuid(),'Goat & Sheep Feed','Balanced feed for goats and sheep.',TRUE),
(gen_random_uuid(),'Veterinary & Accessories','Veterinary products and farming accessories.',TRUE)
ON CONFLICT (name) DO UPDATE SET description=EXCLUDED.description,active=TRUE;

INSERT INTO products (id,sku,category_id,name,description,pack_size,image_url,published,active)
SELECT gen_random_uuid(), item.sku, category.id, item.name,
       item.description, 'To be confirmed', item.image_url, TRUE, TRUE
FROM (VALUES
('HF-WEB-BROILER-STARTER-CRUMBS','Poultry Feed','Broiler Starter Crumbs (23% C.P)','Starter feed for broilers from hatch to approximately two weeks.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-BROILER-GROWER-CRUMBS','Poultry Feed','Broiler Grower Crumbs (21% C.P)','Grower crumbs for broilers during the middle production phase.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-BROILER-GROWER-PELLETS','Poultry Feed','Broiler Grower Pellets (21% C.P)','Grower pellets for broilers during the middle production phase.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-BROILER-FINISHER','Poultry Feed','Broiler Finisher Pellets (19% C.P)','Finisher feed for broilers approaching market weight.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-BROILER-STARTER-GROWER','Poultry Feed','Broiler Starter/Grower','Two-phase starter and grower broiler feed.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-BROILER-GROWER-FINISHER','Poultry Feed','Broiler Grower/Finisher','Two-phase grower and finisher broiler feed.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_broiler_feed_supply.jpg'),
('HF-WEB-LAYER-MASH','Poultry Feed','Layer Mash','Balanced mash formulated for productive laying hens.','https://hyperfeeds.co.zw/assets/img/layer_feed_hyperfeeds.jpg'),
('HF-WEB-LAYER-CRUMBLES','Poultry Feed','Layer Crumbles','Balanced crumble feed formulated for productive laying hens.','https://hyperfeeds.co.zw/assets/img/layer_feed_hyperfeeds.jpg'),
('HF-WEB-ROAD-RUNNER','Poultry Feed','Road Runner Feed','Feed formulated for indigenous and free-range poultry.','https://hyperfeeds.co.zw/assets/img/layer_feed_hyperfeeds_road.jpg'),
('HF-WEB-DOG-FOOD','Pet Food','Hyperfeeds Dog Food','Complete balanced food for active adult dogs.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_dogo_feed_supply.jpg'),
('HF-WEB-RABBIT-PELLETS','Rabbit Feed','Rabbit Pellets','High-fibre balanced pellets for domestic and commercial rabbits.','https://hyperfeeds.co.zw/assets/img/rabbit_pellets.jpg'),
('HF-WEB-GAME-BLOCK','Nutritional Blocks','Game Block','Mineral lick block formulated for wildlife and game.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_game_block.jpg'),
('HF-WEB-PHOS-BLOCK','Nutritional Blocks','Phosphorus Block','Phosphorus and trace-mineral lick block for livestock.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_phos_block.jpg'),
('HF-WEB-WINTER-BLOCK','Nutritional Blocks','Winter Block','Seasonal mineral lick block for pasture-fed livestock.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_winter_block_1.jpg'),
('HF-WEB-PIG-WEANER','Pig Feed','Pig Weaner/Creep Meal','Meal formulated for young pigs during weaning.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-GROWER','Pig Feed','Pig Grower Meal','Meal formulated to support growing pigs.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-FINISHER','Pig Feed','Pig Finisher Meal','Finishing meal for pigs approaching market weight.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-BOAR-SOW','Pig Feed','Pig Boar/Sow Meal','Balanced meal for breeding boars and sows.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-LACTATING-SOW','Pig Feed','Pig Lactating Sow Meal','Meal formulated for lactating sows.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-GROWER-CONC','Pig Feed','Pig Grower Concentrate','Concentrate for grower pig rations.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-FINISHER-CONC','Pig Feed','Pig Finisher Concentrate','Concentrate for finisher pig rations.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-BOAR-SOW-CONC','Pig Feed','Pig Boar/Sow Concentrate','Concentrate for breeding boar and sow rations.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-PIG-LACTATING-CONC','Pig Feed','Pig Lactating Sow Concentrate','Concentrate for lactating sow rations.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_pig_product.jpg'),
('HF-WEB-BEEF-FATTENING','Cattle Feed','Beef Fattening Meal','Performance meal for finishing beef cattle.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-BEEF-SURVIVAL','Cattle Feed','Beef Survival Meal','Supplementary meal for maintaining beef cattle.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-BULL-HEIFER','Cattle Feed','Bull and Heifer Meal','Balanced meal for growing bulls and heifers.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-CALF-STARTER','Cattle Feed','Calf Starter Meal','Starter meal for young calves.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-CALF-GROWER','Cattle Feed','Calf Grower Meal','Grower meal for developing calves.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-DAIRY-18','Cattle Feed','Dairy Meal 18% C.P','Dairy meal formulated to support milk production.','https://hyperfeeds.co.zw/hyperfeeds_beef_product.jpg'),
('HF-WEB-GOAT-SHEEP','Goat & Sheep Feed','Goat and Sheep Feed','Balanced feed for growth and productivity in goats and sheep.','https://hyperfeeds.co.zw/hyperfeeds_goat_crum.jpg'),
('HF-WEB-VET-NEWCASTLE','Veterinary & Accessories','Newcastle Vaccine','Poultry vaccine; use under expert guidance.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-VET-GUMBORO','Veterinary & Accessories','Gumboro Vaccine','Poultry vaccine; use under expert guidance.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-VET-FOWL-POX','Veterinary & Accessories','Fowl Pox Vaccine','Poultry vaccine; use under expert guidance.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-VET-ANTIBIOTICS','Veterinary & Accessories','Veterinary Antibiotics','Veterinary medicine; use only under expert guidance.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-VET-DEWORMERS','Veterinary & Accessories','Dewormers','Animal-health deworming products; use under expert guidance.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-VET-MULTIVITAMINS','Veterinary & Accessories','Animal Multivitamins','Vitamin supplements for animal-health programmes.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-ACC-NIPPLE-DRINKER','Veterinary & Accessories','Nipple Drinkers','Drinking equipment for poultry production.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-ACC-FEEDER','Veterinary & Accessories','Poultry Feeders','Feeding equipment for poultry production.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-ACC-BROODING-LAMP','Veterinary & Accessories','Brooding Lamps','Heating equipment for chick brooding.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg'),
('HF-WEB-ACC-SYRINGE','Veterinary & Accessories','Injection Syringes','Veterinary consumables for animal-health programmes.','https://hyperfeeds.co.zw/assets/img/hyperfeeds_medicine_product.jpg')
) AS item(sku,category_name,name,description,image_url)
JOIN product_categories category ON category.name=item.category_name
ON CONFLICT (sku) DO UPDATE SET
category_id=EXCLUDED.category_id,name=EXCLUDED.name,description=EXCLUDED.description,
pack_size=EXCLUDED.pack_size,image_url=EXCLUDED.image_url,published=TRUE,active=TRUE,updated_at=now();

UPDATE products SET published=FALSE,active=FALSE,updated_at=now() WHERE sku LIKE 'DEMO-%';

INSERT INTO branch_prices (branch_id,product_id,amount,currency)
SELECT branch.id,product.id,
       CASE
         WHEN product.sku LIKE 'HF-WEB-VET-%' OR product.sku LIKE 'HF-WEB-ACC-%' THEN 10.00
         WHEN product.sku LIKE 'HF-WEB-%BLOCK%' THEN 15.00
         ELSE 30.00
       END,'USD'
FROM branches branch CROSS JOIN products product
WHERE branch.active AND branch.id::text LIKE '11000000-%' AND product.sku LIKE 'HF-WEB-%'
ON CONFLICT (branch_id,product_id) WHERE effective_to IS NULL
DO UPDATE SET amount=EXCLUDED.amount,currency=EXCLUDED.currency;

INSERT INTO branch_inventory (branch_id,product_id,on_hand,reserved,low_stock_threshold)
SELECT branch.id,product.id,100,0,10
FROM branches branch CROSS JOIN products product
WHERE branch.active AND branch.id::text LIKE '11000000-%' AND product.sku LIKE 'HF-WEB-%'
ON CONFLICT (branch_id,product_id) DO UPDATE SET
on_hand=EXCLUDED.on_hand,reserved=0,low_stock_threshold=EXCLUDED.low_stock_threshold,
version=branch_inventory.version+1,updated_at=now();
