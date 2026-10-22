-- Тип наклейки зберігається в довіднику моделей тахографів (задається при додаванні моделі).
-- Форма протоколу без наряду бере тип з моделі; NULL = звичайна наклейка.
ALTER TABLE tachograph_attributes ADD COLUMN label_type VARCHAR(20);

UPDATE tachograph_attributes SET label_type = 'SMART_1'
WHERE category = 'MODEL' AND name IN ('DTCO 4.0', 'DTCO 4.0e', 'SE5000 Smart');
UPDATE tachograph_attributes SET label_type = 'SMART_2'
WHERE category = 'MODEL' AND name IN ('DTCO 4.1', 'DTCO 4.1a', 'SE5000 Smart 2');

-- Intellic: EFAS-4.10 / EFAS-4.11 — smart-тахографи 1-ї версії, EFAS-5 — Smart 2
INSERT INTO tachograph_attributes (category, name, label_type) VALUES
    ('MODEL', 'EFAS-4.10', 'SMART_1'),
    ('MODEL', 'EFAS-4.11', 'SMART_1'),
    ('MODEL', 'EFAS-5', 'SMART_2')
ON CONFLICT (category, name) DO UPDATE SET label_type = EXCLUDED.label_type;
