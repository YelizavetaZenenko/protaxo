-- Довідники "Тип" і "Виробник" тахографа у протоколі калібрування (на прохання користувачки 2026-10-22).
-- Уже наявні значення ("Smart", "1381.1232309005", "Siemens" тощо) лишаються.
INSERT INTO tachograph_attributes (category, name) VALUES
    ('TYPE', 'Аналоговий'),
    ('TYPE', 'Цифровий'),
    ('TYPE', 'Smart 1'),
    ('TYPE', 'Smart 2'),
    ('MANUFACTURER', 'Continental Automotive GmbH'),
    ('MANUFACTURER', 'Continental Automotive Technologies GmbH'),
    ('MANUFACTURER', 'Siemens VDO Automotive AG'),
    ('MANUFACTURER', 'Siemens AG'),
    ('MANUFACTURER', 'Mannesmann VDO Kienzle GmbH'),
    ('MANUFACTURER', 'VDO Kienzle'),
    ('MANUFACTURER', 'Stoneridge Electronics AB'),
    ('MANUFACTURER', 'Stoneridge Electronics Ltd'),
    ('MANUFACTURER', 'Intellic GmbH'),
    ('MANUFACTURER', 'EFKON AG'),
    ('MANUFACTURER', 'Actia S.A.'),
    ('MANUFACTURER', 'Motometer GmbH'),
    ('MANUFACTURER', 'Veeder-Root')
ON CONFLICT (category, name) DO NOTHING;
