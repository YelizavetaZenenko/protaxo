ALTER TABLE invoice_items ADD COLUMN catalog_item_id BIGINT REFERENCES catalog_items(id);
