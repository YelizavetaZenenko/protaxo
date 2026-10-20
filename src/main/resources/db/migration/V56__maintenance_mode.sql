-- Режим технічних робіт (docs/Технічні роботи.md): один рядок-перемикач. Поки enabled = true,
-- усі, крім розробника (APP_DEVELOPER_EMAILS), бачать сторінку «Технічні роботи». Зберігається в БД, а не в пам'яті,
-- щоб пережити перезапуск app під час деплою.
CREATE TABLE maintenance_mode (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    changed_at TIMESTAMP,
    changed_by VARCHAR(255)
);

INSERT INTO maintenance_mode (id, enabled) VALUES (1, FALSE);
