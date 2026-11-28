-- Свої форми власності контрагента (docs/Контрагенти.md): додаються з модального вікна форми
-- контрагента, коли потрібної немає серед вбудованих (LegalForm). У клієнта тоді legal_form = 'CUSTOM'
-- і посилання на рядок цього довідника.
CREATE TABLE custom_legal_forms (
    id          BIGSERIAL PRIMARY KEY,
    short_label VARCHAR(20)  NOT NULL,
    full_label  VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_custom_legal_forms_short_label ON custom_legal_forms (lower(short_label));

ALTER TABLE clients ADD COLUMN custom_legal_form_id BIGINT REFERENCES custom_legal_forms (id);
