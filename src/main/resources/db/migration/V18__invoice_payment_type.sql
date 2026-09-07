CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;

-- Keep generated numbers clear of the manually typed ones already in the table.
SELECT setval('invoice_number_seq', GREATEST((SELECT COUNT(*) FROM invoices), 1));

ALTER TABLE invoices ADD COLUMN payment_type VARCHAR(20);
UPDATE invoices SET payment_type = 'CASH' WHERE payment_type IS NULL;
ALTER TABLE invoices ALTER COLUMN payment_type SET NOT NULL;

ALTER TABLE invoices DROP COLUMN organization_name;
