-- ClientRequest accepts a 12-digit ІПН, but the column only held 9 characters, so every such
-- save failed with "value too long" — which the service then reported as a duplicate EDRPOU.
ALTER TABLE clients ALTER COLUMN code TYPE VARCHAR(12);

-- Records are soft-deleted (deleted_at set, hidden from the app by @SQLRestriction), but the
-- plain UNIQUE constraints still counted them: re-creating a deleted client/vehicle/tachograph
-- with the same EDRPOU/VIN/serial number failed as "already exists" although nothing visible
-- had it. Uniqueness now applies to live rows only.
ALTER TABLE clients DROP CONSTRAINT clients_edrpou_key;
UPDATE clients SET edrpou = NULL WHERE edrpou = '';
CREATE UNIQUE INDEX uq_clients_edrpou_active ON clients (edrpou) WHERE deleted_at IS NULL;

ALTER TABLE vehicles DROP CONSTRAINT vehicles_vin_key;
CREATE UNIQUE INDEX uq_vehicles_vin_active ON vehicles (vin) WHERE deleted_at IS NULL;

ALTER TABLE tachographs DROP CONSTRAINT tachographs_serial_number_key;
CREATE UNIQUE INDEX uq_tachographs_serial_number_active ON tachographs (serial_number) WHERE deleted_at IS NULL;
