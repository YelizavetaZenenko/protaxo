-- Розширений журнал дій: IP-адреса, з якої виконано дію, і індекси для фільтрів сторінки /audit-log
-- (історія одного елемента, дії одного користувача).
ALTER TABLE audit_log ADD COLUMN ip_address VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_username ON audit_log (username);
