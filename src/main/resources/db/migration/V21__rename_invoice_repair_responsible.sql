ALTER TABLE invoices RENAME COLUMN transport_responsible_name TO repair_responsible_name;

UPDATE field_suggestions SET category = 'REPAIR_RESPONSIBLE' WHERE category = 'TRANSPORT_RESPONSIBLE';
