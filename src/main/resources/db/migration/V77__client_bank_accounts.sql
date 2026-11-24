-- Додаткові поточні рахунки контрагента (docs/Контрагенти.md). Основний рахунок лишається в
-- clients.iban / bank_name / mfo — його друкують договір і рахунки; тут — решта, у порядку position.
CREATE TABLE client_bank_accounts (
    client_id BIGINT       NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    position  INTEGER      NOT NULL,
    iban      VARCHAR(34)  NOT NULL,
    bank_name VARCHAR(255),
    mfo       VARCHAR(6),
    PRIMARY KEY (client_id, position)
);
