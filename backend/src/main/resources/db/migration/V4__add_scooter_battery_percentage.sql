ALTER TABLE scooters
    ADD COLUMN battery_percent INTEGER NOT NULL DEFAULT 100
        CHECK (battery_percent BETWEEN 0 AND 100);
