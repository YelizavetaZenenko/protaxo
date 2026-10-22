-- Новий список "Підстава для перевірки та адаптації" (на прохання користувачки 2026-10-22).
-- Старі п'ять варіантів з V57 прибираємо з довідника; вже збережені протоколи не чіпаємо —
-- у них лишається старий текст (форма його все одно покаже при редагуванні).
-- Значення, додані вручну через "+ Додати підставу", лишаються.
-- Порядок у списку задає TachographAttributeService, а не порядок вставки.
DELETE FROM tachograph_attributes
WHERE category = 'INSPECTION_REASON'
  AND name IN ('установлення', 'дотримання періодичності перевірки', 'ремонт',
               'заміна трансмісійних агрегатів або пневматичних шин іншого розміру', 'інші підстави');

INSERT INTO tachograph_attributes (category, name) VALUES
    ('INSPECTION_REASON', 'Встановлення'),
    ('INSPECTION_REASON', 'Заміна несправного тахографа'),
    ('INSPECTION_REASON', 'Заміна пневматичних шин'),
    ('INSPECTION_REASON', 'Зміна реєстрації ТЗ'),
    ('INSPECTION_REASON', 'Первинна реєстрація ТЗ'),
    ('INSPECTION_REASON', 'Періодичність'),
    ('INSPECTION_REASON', 'Ремонт'),
    ('INSPECTION_REASON', 'Інші підстави')
ON CONFLICT (category, name) DO NOTHING;
