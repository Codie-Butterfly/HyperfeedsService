ALTER TABLE chick_bookings
    ADD COLUMN pickup_branch_id UUID REFERENCES branches(id),
    ADD COLUMN chick_breed_config_id UUID REFERENCES chick_breed_configs(id),
    ADD COLUMN chick_type VARCHAR(20),
    ADD COLUMN breed VARCHAR(120);

UPDATE chick_bookings booking
SET pickup_branch_id = batch.branch_id,
    chick_type = batch.chick_type,
    breed = batch.breed,
    chick_breed_config_id = config.id
FROM chick_batches batch
LEFT JOIN chick_breed_configs config
  ON config.chick_type = batch.chick_type
 AND lower(config.breed) = lower(batch.breed)
WHERE booking.batch_id = batch.id;

ALTER TABLE chick_bookings ALTER COLUMN batch_id DROP NOT NULL;

CREATE INDEX idx_chick_bookings_pickup_branch
    ON chick_bookings(pickup_branch_id, booking_batch_id, status);
