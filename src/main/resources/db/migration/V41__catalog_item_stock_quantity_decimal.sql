-- stock_quantity was INTEGER while InvoiceItem.quantity is NUMERIC(12,3) (fractional units, e.g.
-- 2.5 liters of oil) — deducting/restoring stock via Integer.intValue() truncated the fraction on
-- every fractional sale, silently drifting stock upward relative to reality. Widen the column to
-- match quantity's precision so the fix in InvoiceService can track fractions exactly.
ALTER TABLE catalog_items ALTER COLUMN stock_quantity TYPE NUMERIC(12,3) USING stock_quantity::numeric(12,3);
