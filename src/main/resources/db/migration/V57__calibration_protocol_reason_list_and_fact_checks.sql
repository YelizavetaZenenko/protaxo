-- "Підстава для перевірки та адаптації" стає випадаючим списком з довідника tachograph_attributes
-- (та сама схема, що й марка/модель/тип/виробник: список + додавання нового значення з модального вікна).
-- Початкові варіанти — текст, який раніше стояв підказкою в полі (і є на бланку протоколу).
INSERT INTO tachograph_attributes (category, name) VALUES
    ('INSPECTION_REASON', 'установлення'),
    ('INSPECTION_REASON', 'дотримання періодичності перевірки'),
    ('INSPECTION_REASON', 'ремонт'),
    ('INSPECTION_REASON', 'заміна трансмісійних агрегатів або пневматичних шин іншого розміру'),
    ('INSPECTION_REASON', 'інші підстави')
ON CONFLICT (category, name) DO NOTHING;

-- Підтема "Реєстрування факту": чотири нові галочки (на бланку протоколу їх немає)
ALTER TABLE calibration_protocols
    ADD COLUMN verification_plate_present BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mark_present BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN no_manipulation_devices BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN seals_intact BOOLEAN NOT NULL DEFAULT FALSE;

-- Три старі поля "зареєстровано" лишаються текстом (друкуються в п. 11–13 бланку), але тепер це
-- галочки зі значенням "Так"; будь-що інше (напр. "Ні") = не відмічено.
UPDATE calibration_protocols SET cover_opening_registered =
    CASE WHEN lower(trim(cover_opening_registered)) LIKE 'так%' THEN 'Так' END;
UPDATE calibration_protocols SET power_cutoff_registered =
    CASE WHEN lower(trim(power_cutoff_registered)) LIKE 'так%' THEN 'Так' END;
UPDATE calibration_protocols SET pulse_sensor_interruption_registered =
    CASE WHEN lower(trim(pulse_sensor_interruption_registered)) LIKE 'так%' THEN 'Так' END;
