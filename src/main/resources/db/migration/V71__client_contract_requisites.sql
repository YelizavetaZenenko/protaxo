-- Реквізити замовника для договору (docs/Договори.md): юридична адреса, р/р (IBAN), банк, МФО,
-- посада підписанта («Директор») і на підставі чого він діє («Статуту»). Усі поля необов'язкові:
-- порожні друкуються в договорі лінією для заповнення від руки.
ALTER TABLE clients
    ADD COLUMN legal_address      VARCHAR(500),
    ADD COLUMN iban               VARCHAR(34),
    ADD COLUMN bank_name          VARCHAR(255),
    ADD COLUMN mfo                VARCHAR(6),
    ADD COLUMN signatory_position VARCHAR(100),
    ADD COLUMN signatory_basis    VARCHAR(255);
