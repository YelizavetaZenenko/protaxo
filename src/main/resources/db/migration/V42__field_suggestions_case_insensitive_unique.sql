-- remember() only checked existsByCategoryAndValue (exact, case-sensitive) with no DB constraint
-- backing it up — "Іваненко" and "іваненко" ended up as separate rows, and two concurrent saves of
-- the same brand-new value could both pass the check before either committed. Dedupe first (keep
-- the earliest row per case-insensitive value) so the new unique index below has something valid
-- to enforce against already-existing data.
DELETE FROM field_suggestions fs
USING field_suggestions fs2
WHERE fs.category = fs2.category
  AND lower(fs.value) = lower(fs2.value)
  AND fs.id > fs2.id;

CREATE UNIQUE INDEX uq_field_suggestions_category_value_ci
    ON field_suggestions (category, lower(value));
