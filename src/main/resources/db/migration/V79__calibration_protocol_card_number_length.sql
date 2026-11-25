-- Форма й довідник карток майстра дозволяють номер до 17 символів, а тут лишалось 15:
-- протокол із довшим номером не зберігався («value too long» → 500).
ALTER TABLE calibration_protocols ALTER COLUMN card_number TYPE VARCHAR(20);
