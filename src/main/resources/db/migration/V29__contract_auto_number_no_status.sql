-- Contract numbers move from manual entry to an auto-assigned sequence (same approach as
-- invoice_number_seq), and the status concept is dropped entirely — every contract is simply
-- active by definition, there is no "suspended"/"terminated" state in this iteration.

CREATE SEQUENCE contract_number_seq START WITH 1 INCREMENT BY 1;

-- Keep generated numbers clear of any manually typed ones already in the table.
SELECT setval('contract_number_seq', GREATEST((SELECT COUNT(*) FROM contracts), 1));

ALTER TABLE contracts DROP COLUMN status;
