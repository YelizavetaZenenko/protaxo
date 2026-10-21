-- "Розмір пневматичних шин" у протоколі стає випадаючим списком з довідника tachograph_attributes
-- (як марка/модель/підстава: список + додавання нового значення з модального вікна).
-- Початкові варіанти — поширені розміри вантажних шин плюс усе, що вже вписували в протоколи.
INSERT INTO tachograph_attributes (category, name) VALUES
    ('TIRE_SIZE', '205/75 R17,5'),
    ('TIRE_SIZE', '215/75 R17,5'),
    ('TIRE_SIZE', '225/75 R17,5'),
    ('TIRE_SIZE', '235/75 R17,5'),
    ('TIRE_SIZE', '245/70 R19,5'),
    ('TIRE_SIZE', '265/70 R19,5'),
    ('TIRE_SIZE', '285/70 R19,5'),
    ('TIRE_SIZE', '275/70 R22,5'),
    ('TIRE_SIZE', '295/60 R22,5'),
    ('TIRE_SIZE', '295/80 R22,5'),
    ('TIRE_SIZE', '315/60 R22,5'),
    ('TIRE_SIZE', '315/70 R22,5'),
    ('TIRE_SIZE', '315/80 R22,5'),
    ('TIRE_SIZE', '385/55 R22,5'),
    ('TIRE_SIZE', '385/65 R22,5')
ON CONFLICT (category, name) DO NOTHING;

INSERT INTO tachograph_attributes (category, name)
SELECT DISTINCT 'TIRE_SIZE', trim(tire_size)
FROM calibration_protocols
WHERE tire_size IS NOT NULL AND trim(tire_size) <> ''
ON CONFLICT (category, name) DO NOTHING;
