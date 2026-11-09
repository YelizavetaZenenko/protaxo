-- ФОП на загальній системі оподаткування, НЕ платник ПДВ (рішення власниці 2026-11-09).
-- Неплатник не може виділяти ПДВ у документах, тож рядки нарядів, збережені з 20% за
-- старим правилом (V45), стають «Без ПДВ»: сума рядка (до оплати) не змінюється,
-- лише ПДВ = 0, а сума без ПДВ = сума.
UPDATE finance_settings SET tax_system = 'GENERAL', vat_payer = FALSE WHERE id = 1;

UPDATE invoice_items
SET vat_rate = 'NO_VAT', vat_amount = 0, amount_without_vat = amount
WHERE vat_rate <> 'NO_VAT' OR vat_amount <> 0;
