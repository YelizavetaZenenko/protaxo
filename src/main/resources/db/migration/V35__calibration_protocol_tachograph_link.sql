ALTER TABLE calibration_protocols ADD COLUMN tachograph_id BIGINT REFERENCES tachographs(id);
