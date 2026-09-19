ALTER TABLE calibration_protocols ADD COLUMN seal_numbers VARCHAR(255);
ALTER TABLE calibration_protocols ADD COLUMN qr_hash VARCHAR(64) UNIQUE;
