CREATE TABLE hubs (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity >= 0)
);

CREATE TABLE scooters (
    id UUID PRIMARY KEY,
    type TEXT NOT NULL CHECK (length(trim(type)) > 0),
    hub_id UUID NOT NULL REFERENCES hubs(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RESERVED', 'IN_USE', 'MAINTENANCE', 'RETIRED'))
);

CREATE INDEX scooters_hub_id_idx ON scooters(hub_id);
