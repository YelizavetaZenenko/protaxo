-- Як V43 для контрагентів/авто/тахографів: видалений запис більше не займає значення.
-- users.email: видаленого працівника не можна було запросити знову («Користувач з такою поштою вже існує»).
-- calibration_protocols.invoice_id: після видалення протоколу наряд назавжди лишався без протоколу.
ALTER TABLE users DROP CONSTRAINT users_email_key;
CREATE UNIQUE INDEX uq_users_email_active ON users (email) WHERE deleted_at IS NULL;

ALTER TABLE calibration_protocols DROP CONSTRAINT uq_calibration_protocols_invoice_id;
CREATE UNIQUE INDEX uq_calibration_protocols_invoice_id ON calibration_protocols (invoice_id) WHERE deleted_at IS NULL;
