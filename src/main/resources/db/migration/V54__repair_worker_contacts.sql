-- Контактна й робоча інформація робітника (картка робітника на сторінці «Користувачі»).
ALTER TABLE repair_workers ADD COLUMN phone VARCHAR(50);
ALTER TABLE repair_workers ADD COLUMN email VARCHAR(255);
ALTER TABLE repair_workers ADD COLUMN address VARCHAR(500);
ALTER TABLE repair_workers ADD COLUMN hire_date DATE;
ALTER TABLE repair_workers ADD COLUMN workshop_card_number VARCHAR(50);
ALTER TABLE repair_workers ADD COLUMN workshop_card_valid_until DATE;
ALTER TABLE repair_workers ADD COLUMN emergency_contact VARCHAR(255);
ALTER TABLE repair_workers ADD COLUMN notes VARCHAR(2000);
