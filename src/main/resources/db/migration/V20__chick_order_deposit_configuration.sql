INSERT INTO system_configs(config_key, config_value) VALUES
    ('CHICK_ORDER_DEPOSIT_ENABLED', 'false'),
    ('CHICK_ORDER_DEPOSIT_PERCENTAGE', '0')
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE chick_bookings
    ADD COLUMN deposit_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deposit_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN deposit_amount NUMERIC(19,2) NOT NULL DEFAULT 0;
