ALTER TABLE hubs ADD COLUMN department VARCHAR(120) NOT NULL DEFAULT 'Unassigned';
UPDATE hubs SET department = name;
ALTER TABLE hubs ADD CONSTRAINT hubs_name_unique UNIQUE (name);
