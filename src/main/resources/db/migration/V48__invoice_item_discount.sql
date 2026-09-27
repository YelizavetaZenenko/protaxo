-- Знижка на рядок наряду у відсотках; сума рядка (з ПДВ) вже враховує знижку.
ALTER TABLE invoice_items
    ADD COLUMN discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;
