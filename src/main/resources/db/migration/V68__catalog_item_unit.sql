-- Одиниця виміру позиції каталогу: PIECE (шт) або METER (м). Усі наявні позиції — штуки.
-- Для послуг поле не використовується (у документах — «послуга").
ALTER TABLE catalog_items ADD COLUMN unit VARCHAR(10) NOT NULL DEFAULT 'PIECE';
