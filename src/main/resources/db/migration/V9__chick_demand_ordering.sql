ALTER TABLE chick_batches
    ADD COLUMN chick_type VARCHAR(20),
    ADD COLUMN cutoff_at TIMESTAMPTZ,
    ADD COLUMN delivery_date DATE,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';

UPDATE chick_batches
SET chick_type = CASE WHEN lower(breed) LIKE '%layer%' THEN 'LAYER' ELSE 'BROILER' END,
    cutoff_at = (hatch_date::timestamp - interval '7 days') AT TIME ZONE 'Africa/Harare',
    delivery_date = hatch_date;

ALTER TABLE chick_batches
    ALTER COLUMN chick_type SET NOT NULL,
    ALTER COLUMN cutoff_at SET NOT NULL,
    ALTER COLUMN delivery_date SET NOT NULL,
    ADD CONSTRAINT ck_chick_batch_type CHECK (chick_type IN ('BROILER', 'LAYER')),
    ADD CONSTRAINT ck_chick_batch_status CHECK (status IN ('OPEN', 'CLOSED', 'DELIVERING', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_chick_batch_dates CHECK (delivery_date >= cutoff_at::date);

ALTER TABLE chick_bookings
    ADD COLUMN unit_price NUMERIC(19,2),
    ADD COLUMN total_amount NUMERIC(19,2),
    ADD COLUMN currency CHAR(3),
    ADD COLUMN delivery_date_snapshot DATE;

UPDATE chick_bookings booking
SET unit_price = batch.price_per_chick,
    total_amount = booking.quantity * batch.price_per_chick,
    currency = batch.currency,
    delivery_date_snapshot = batch.delivery_date
FROM chick_batches batch
WHERE booking.batch_id = batch.id;

ALTER TABLE chick_bookings
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN total_amount SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN expires_at DROP NOT NULL,
    ADD CONSTRAINT ck_chick_booking_amount CHECK (unit_price >= 0 AND total_amount >= 0);

CREATE INDEX idx_chick_batches_ordering_window
    ON chick_batches(branch_id, chick_type, breed, cutoff_at)
    WHERE active AND status = 'OPEN';

CREATE INDEX idx_chick_bookings_batch_status
    ON chick_bookings(batch_id, status);
