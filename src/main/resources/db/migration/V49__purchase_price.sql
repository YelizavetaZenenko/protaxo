-- Ціна закупівлі товару (необов'язкова) і її знімок у рядку наряду на момент продажу,
-- щоб зміна ціни в каталозі не переписувала собівартість уже проданого.
ALTER TABLE catalog_items
    ADD COLUMN purchase_price NUMERIC(12, 2);

ALTER TABLE invoice_items
    ADD COLUMN purchase_price NUMERIC(12, 2);
