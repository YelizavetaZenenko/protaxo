-- Форма власності контрагента (ТОВ, ФОП, ПП…; docs/Контрагенти.md). У старих записів NULL — назва як є.
ALTER TABLE clients ADD COLUMN legal_form VARCHAR(10);
