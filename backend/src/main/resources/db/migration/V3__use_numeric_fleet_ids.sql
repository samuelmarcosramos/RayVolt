CREATE SEQUENCE hubs_id_seq START WITH 1;
CREATE SEQUENCE scooters_id_seq START WITH 1;

ALTER TABLE hubs ADD COLUMN numeric_id BIGINT DEFAULT nextval('"${flyway:defaultSchema}".hubs_id_seq') NOT NULL;
ALTER TABLE scooters ADD COLUMN numeric_id BIGINT DEFAULT nextval('"${flyway:defaultSchema}".scooters_id_seq') NOT NULL;

-- Preserve the original UUID records as a migration snapshot.
ALTER TABLE hubs RENAME TO legacy_hubs;
ALTER TABLE scooters RENAME TO legacy_scooters;

CREATE TABLE hubs (
    id BIGINT DEFAULT nextval('"${flyway:defaultSchema}".hubs_id_seq') PRIMARY KEY CHECK (id > 0),
    name VARCHAR(120) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity >= 0),
    lat DOUBLE PRECISION,
    lon DOUBLE PRECISION,
    CHECK ((lat IS NULL AND lon IS NULL)
        OR (lat IS NOT NULL AND lon IS NOT NULL
            AND lat BETWEEN -90 AND 90 AND lon BETWEEN -180 AND 180))
);

INSERT INTO hubs (id, name, capacity, lat, lon)
SELECT numeric_id, name, capacity, lat, lon FROM legacy_hubs;

CREATE TABLE scooters (
    id BIGINT DEFAULT nextval('"${flyway:defaultSchema}".scooters_id_seq') PRIMARY KEY CHECK (id > 0),
    type TEXT NOT NULL CHECK (length(trim(type)) > 0),
    hub_id BIGINT NOT NULL REFERENCES hubs(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED', 'IN_USE', 'MAINTENANCE', 'RETIRED'))
);

INSERT INTO scooters (id, type, hub_id, status)
SELECT s.numeric_id, s.type, h.numeric_id, s.status
FROM legacy_scooters s JOIN legacy_hubs h ON h.id = s.hub_id;

CREATE INDEX scooters_numeric_hub_id_idx ON scooters(hub_id);
