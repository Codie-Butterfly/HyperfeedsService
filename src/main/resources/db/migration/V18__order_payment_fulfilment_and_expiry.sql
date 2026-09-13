CREATE TABLE system_configs (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO system_configs(config_key, config_value)
VALUES ('UNPAID_ORDER_EXPIRY_HOURS', '24')
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'PAY_ON_APP',
    ADD COLUMN fulfilment_method VARCHAR(20) NOT NULL DEFAULT 'PICKUP',
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_orders_unpaid_expiry
    ON orders(expires_at) WHERE status = 'AWAITING_PAYMENT_AT_SHOP';
