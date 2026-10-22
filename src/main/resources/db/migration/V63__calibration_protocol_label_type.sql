-- Тип наклейки (STANDARD / SMART_1 / SMART_2), обраний вручну у формі протоколу без наряд-заказу.
-- Для протоколу з наряд-заказом лишається NULL — тип визначає послуга калібрування в наряді.
ALTER TABLE calibration_protocols ADD COLUMN label_type VARCHAR(20);
