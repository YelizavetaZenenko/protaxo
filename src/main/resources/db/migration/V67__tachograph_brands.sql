-- Довідник "Марка" тахографа у протоколі калібрування (на прохання користувачки 2026-10-22).
-- Уже наявне "Siemens" лишається.
INSERT INTO tachograph_attributes (category, name) VALUES
    ('BRAND', 'VDO'),
    ('BRAND', 'Continental VDO'),
    ('BRAND', 'Siemens VDO'),
    ('BRAND', 'Kienzle'),
    ('BRAND', 'Stoneridge'),
    ('BRAND', 'Intellic'),
    ('BRAND', 'EFKON'),
    ('BRAND', 'Actia'),
    ('BRAND', 'Motometer'),
    ('BRAND', 'Veeder-Root')
ON CONFLICT (category, name) DO NOTHING;
