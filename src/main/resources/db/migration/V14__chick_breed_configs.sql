CREATE TABLE chick_breed_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chick_type VARCHAR(20) NOT NULL CHECK (chick_type IN ('BROILER', 'LAYER')),
    breed VARCHAR(120) NOT NULL,
    price_per_chick NUMERIC(19,2) NOT NULL CHECK (price_per_chick >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_chick_breed_config
    ON chick_breed_configs(chick_type, lower(breed));

INSERT INTO chick_breed_configs(chick_type, breed, price_per_chick, currency, available)
SELECT chick_type, breed, min(price_per_chick), min(trim(currency)),
       bool_or(active AND status = 'OPEN')
FROM chick_batches
GROUP BY chick_type, breed
ON CONFLICT DO NOTHING;
