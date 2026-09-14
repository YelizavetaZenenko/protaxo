-- stock_quantity moves from a fractional numeric to a plain whole-number count — nothing sold
-- by fractional units in this catalog, and the edit form now enforces integers only. Existing
-- values are already whole numbers (200.000, 1.000), so the cast is safe.
ALTER TABLE catalog_items ALTER COLUMN stock_quantity TYPE INTEGER USING stock_quantity::INTEGER;
