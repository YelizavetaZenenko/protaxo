-- Панель бухгалтера (docs/Фінансовий облік.md): стан акта виконаних робіт за нарядом і
-- налаштування обліку (назва ФОП, система оподаткування, джерело банківської виписки).

-- Ланцюжок документів: наряд → рахунок → оплата → акт. Акт друкується з наряду, тут лише
-- відмітка, чи його вже сформовано й чи повернувся він підписаним.
ALTER TABLE invoices ADD COLUMN act_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CREATED';

-- Один рядок на всю систему (id = 1).
CREATE TABLE finance_settings (
    id                     BIGINT PRIMARY KEY,
    business_name          VARCHAR(255),
    tax_system             VARCHAR(30),
    vat_payer              BOOLEAN NOT NULL DEFAULT FALSE,
    bank_statement_source  VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    version                BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ,
    updated_by             VARCHAR(255)
);

INSERT INTO finance_settings (id) VALUES (1);
