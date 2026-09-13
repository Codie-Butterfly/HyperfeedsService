CREATE TABLE chick_booking_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

CREATE UNIQUE INDEX uq_single_open_chick_booking_batch
    ON chick_booking_batches ((status)) WHERE status = 'OPEN';

-- Preserve current customer booking behaviour until the main manager replaces
-- this initial period with a deliberately configured batch.
INSERT INTO chick_booking_batches(name, start_date, end_date, status)
SELECT 'Initial booking batch', current_date,
       greatest(current_date, coalesce(max(delivery_date), current_date + 30)),
       'OPEN'
FROM chick_batches
WHERE active AND status = 'OPEN';
