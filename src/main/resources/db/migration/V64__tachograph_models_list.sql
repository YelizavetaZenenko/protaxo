-- Довідник моделей тахографів (на прохання користувачки 2026-10-22): аналогові, цифрові,
-- Smart 1 і Smart 2. Тип наклейки протоколу без наряду визначається за моделлю
-- (labelTypeForModel у calibration-protocols/form.html): DTCO 4.0/4.0e і "SE5000 Smart" — Smart 1,
-- DTCO 4.1/4.1a і "SE5000 Smart 2" — Smart 2, решта — звичайна наклейка.
INSERT INTO tachograph_attributes (category, name) VALUES
    -- аналогові
    ('MODEL', '1314'),
    ('MODEL', '1318'),
    ('MODEL', '1319'),
    ('MODEL', '1324'),
    ('MODEL', 'MTCO 1324'),
    ('MODEL', '2400'),
    ('MODEL', '8400'),
    -- цифрові (1-ше покоління)
    ('MODEL', 'DTCO 1381 Rel. 1.3'),
    ('MODEL', 'DTCO 1381 Rel. 1.4'),
    ('MODEL', 'DTCO 1381 Rel. 2.0'),
    ('MODEL', 'DTCO 1381 Rel. 2.1'),
    ('MODEL', 'DTCO 1381 Rel. 2.2'),
    ('MODEL', 'DTCO 1381 Rel. 3.0'),
    ('MODEL', 'SE5000'),
    ('MODEL', 'SE5000 Exakt'),
    ('MODEL', 'SE5000 Exakt Duo'),
    ('MODEL', 'SE5000 Exakt Duo2'),
    ('MODEL', 'EFAS-3'),
    ('MODEL', 'EFAS-4'),
    -- Smart 1
    ('MODEL', 'DTCO 4.0'),
    ('MODEL', 'DTCO 4.0e'),
    ('MODEL', 'SE5000 Smart'),
    -- Smart 2
    ('MODEL', 'DTCO 4.1'),
    ('MODEL', 'DTCO 4.1a'),
    ('MODEL', 'SE5000 Smart 2')
ON CONFLICT (category, name) DO NOTHING;
