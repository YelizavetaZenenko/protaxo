-- Внутрішній коментар до протоколу: лише для обліку в сервісі, ніде не друкується
-- (ні в PDF, ні на наклейці, ні на публічній сторінці перевірки за QR).
ALTER TABLE calibration_protocols ADD COLUMN internal_comment TEXT;
