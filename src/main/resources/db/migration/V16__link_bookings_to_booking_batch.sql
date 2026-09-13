ALTER TABLE chick_booking_batches
    ADD COLUMN delivery_notification_sent_at TIMESTAMPTZ;

ALTER TABLE chick_bookings
    ADD COLUMN booking_batch_id UUID REFERENCES chick_booking_batches(id);

CREATE INDEX idx_chick_bookings_booking_batch
    ON chick_bookings(booking_batch_id, status);

-- Associate legacy active bookings with the initial open booking batch.
UPDATE chick_bookings
SET booking_batch_id = (
    SELECT id FROM chick_booking_batches WHERE status = 'OPEN' LIMIT 1
)
WHERE booking_batch_id IS NULL
  AND status IN ('ORDERED', 'CONFIRMED');
