-- Поля наклейки смарт-тахографа (Smart 1 / Smart 2), див. docs/Print Agent.md
ALTER TABLE calibration_protocols ADD COLUMN load_type VARCHAR(50);
ALTER TABLE calibration_protocols ADD COLUMN ext_gnss VARCHAR(50);
ALTER TABLE calibration_protocols ADD COLUMN gnss_serial_number VARCHAR(100);
ALTER TABLE calibration_protocols ADD COLUMN dsrc_serial_number VARCHAR(100);

-- Одна послуга "Smart" стає двома: Smart 1 (стара, перейменована — зберігає ціну й ПДВ) і Smart 2.
UPDATE catalog_items SET name = 'Калібрування тахографа-Smart 1'
WHERE name = 'Калібрування тахографа-Smart' AND deleted_at IS NULL;

INSERT INTO catalog_items (type, name, base_price, stock_quantity)
SELECT 'SERVICE', 'Калібрування тахографа-Smart 1', 900.00, NULL
WHERE NOT EXISTS (SELECT 1 FROM catalog_items WHERE name = 'Калібрування тахографа-Smart 1' AND deleted_at IS NULL);

INSERT INTO catalog_items (type, name, base_price, stock_quantity, vat_rate)
SELECT 'SERVICE', 'Калібрування тахографа-Smart 2', s1.base_price, NULL, s1.vat_rate
FROM catalog_items s1
WHERE s1.name = 'Калібрування тахографа-Smart 1' AND s1.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM catalog_items WHERE name = 'Калібрування тахографа-Smart 2' AND deleted_at IS NULL)
ORDER BY s1.id
LIMIT 1;
