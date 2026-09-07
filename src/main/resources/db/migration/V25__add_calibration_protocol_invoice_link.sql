ALTER TABLE calibration_protocols ADD COLUMN invoice_id BIGINT REFERENCES invoices(id);
ALTER TABLE calibration_protocols ADD CONSTRAINT uq_calibration_protocols_invoice_id UNIQUE (invoice_id);
CREATE INDEX idx_calibration_protocols_invoice_id ON calibration_protocols (invoice_id);
