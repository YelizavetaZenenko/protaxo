-- Хто і коли востаннє змінив внутрішній коментар протоколу (ПІБ з профілю на момент збереження).
ALTER TABLE calibration_protocols
    ADD COLUMN internal_comment_author VARCHAR(255),
    ADD COLUMN internal_comment_updated_at TIMESTAMP;
