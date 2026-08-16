-- Temporary customer-facing demo catalogue. All records use stable identifiers so
-- they can be edited by administrators without duplicate seed rows.

INSERT INTO branches (id, code, name, address, phone_number, whatsapp_number, opening_hours, collection_enabled, active)
VALUES ('10000000-0000-0000-0000-000000000001', 'DEMO-HRE', 'Hyperfeeds Demo Branch',
        '123 Demo Road, Harare, Zimbabwe', '+263771000000', '+263771000000',
        'Monday–Friday 08:00–17:00; Saturday 08:00–13:00', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, address = EXCLUDED.address, phone_number = EXCLUDED.phone_number,
    whatsapp_number = EXCLUDED.whatsapp_number, opening_hours = EXCLUDED.opening_hours,
    collection_enabled = TRUE, active = TRUE, updated_at = now();

INSERT INTO product_categories (id, name, description, active) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Poultry Feed', 'Demo feeds for broilers and layers.', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'Cattle Feed', 'Demo feeds and supplements for cattle.', TRUE),
    ('20000000-0000-0000-0000-000000000003', 'Pig Feed', 'Demo feeds for pig production.', TRUE),
    ('20000000-0000-0000-0000-000000000004', 'Pet Food', 'Demo food for household pets.', TRUE)
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description, active = TRUE;

INSERT INTO products (id, sku, category_id, name, description, pack_size, published, active) VALUES
    ('30000000-0000-0000-0000-000000000001', 'DEMO-BSC-50', '20000000-0000-0000-0000-000000000001', 'Broiler Starter Crumbs', 'Demo starter feed for young broilers.', '50 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000002', 'DEMO-BGP-50', '20000000-0000-0000-0000-000000000001', 'Broiler Grower Pellets', 'Demo grower feed for broilers.', '50 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000003', 'DEMO-LM-50', '20000000-0000-0000-0000-000000000001', 'Layers Mash', 'Demo balanced feed for laying hens.', '50 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000004', 'DEMO-BCF-50', '20000000-0000-0000-0000-000000000002', 'Beef Cattle Finisher', 'Demo finishing ration for beef cattle.', '50 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000005', 'DEMO-DC-25', '20000000-0000-0000-0000-000000000002', 'Dairy Concentrate', 'Demo concentrate for dairy cattle.', '25 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000006', 'DEMO-PGM-50', '20000000-0000-0000-0000-000000000003', 'Pig Grower Meal', 'Demo grower ration for pigs.', '50 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000007', 'DEMO-DOG-20', '20000000-0000-0000-0000-000000000004', 'Adult Dog Meal', 'Demo complete meal for adult dogs.', '20 kg', TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000008', 'DEMO-PUP-10', '20000000-0000-0000-0000-000000000004', 'Puppy Meal', 'Demo complete meal for puppies.', '10 kg', TRUE, TRUE)
ON CONFLICT (sku) DO UPDATE SET
    category_id = EXCLUDED.category_id, name = EXCLUDED.name, description = EXCLUDED.description,
    pack_size = EXCLUDED.pack_size, published = TRUE, active = TRUE, updated_at = now();

INSERT INTO branch_prices (id, branch_id, product_id, amount, currency) VALUES
    ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 32.00, 'USD'),
    ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 31.50, 'USD'),
    ('40000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', 30.00, 'USD'),
    ('40000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000004', 29.00, 'USD'),
    ('40000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000005', 18.00, 'USD'),
    ('40000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000006', 28.50, 'USD'),
    ('40000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000007', 24.00, 'USD'),
    ('40000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000008', 14.00, 'USD')
ON CONFLICT (branch_id, product_id) WHERE effective_to IS NULL
DO UPDATE SET amount = EXCLUDED.amount, currency = EXCLUDED.currency;

INSERT INTO branch_inventory (branch_id, product_id, on_hand, reserved, low_stock_threshold)
SELECT '10000000-0000-0000-0000-000000000001', id, 100, 0, 10
FROM products WHERE sku LIKE 'DEMO-%'
ON CONFLICT (branch_id, product_id) DO UPDATE SET
    on_hand = EXCLUDED.on_hand, reserved = 0, low_stock_threshold = EXCLUDED.low_stock_threshold,
    version = branch_inventory.version + 1, updated_at = now();

INSERT INTO chick_batches (id, branch_id, breed, hatch_date, available_quantity, reserved_quantity, price_per_chick, currency, active)
VALUES
    ('50000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Ross 308 Broiler', current_date + 14, 500, 0, 1.25, 'USD', TRUE),
    ('50000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Lohmann Brown Layer', current_date + 21, 300, 0, 1.50, 'USD', TRUE)
ON CONFLICT (id) DO UPDATE SET
    hatch_date = EXCLUDED.hatch_date, available_quantity = EXCLUDED.available_quantity,
    reserved_quantity = 0, price_per_chick = EXCLUDED.price_per_chick, active = TRUE;

INSERT INTO announcements (id, title, body, branch_id, published_from, published_until, active)
VALUES ('60000000-0000-0000-0000-000000000001', 'Welcome to the Hyperfeeds demo',
        'Browse demo products, check stock, reserve chicks and test checkout.',
        '10000000-0000-0000-0000-000000000001', now() - interval '1 day', NULL, TRUE)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, body = EXCLUDED.body, active = TRUE;

INSERT INTO specials (id, product_id, branch_id, promotional_price, currency, starts_at, ends_at, active)
VALUES ('70000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001', 29.99, 'USD', now() - interval '1 day', now() + interval '90 days', TRUE)
ON CONFLICT (id) DO UPDATE SET
    promotional_price = EXCLUDED.promotional_price, starts_at = EXCLUDED.starts_at,
    ends_at = EXCLUDED.ends_at, active = TRUE;
