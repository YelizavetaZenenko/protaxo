-- Phone numbers moved from the plain +380XXXXXXXXX format to +38(0XX)-XXX-XX-XX
-- (application-level validation was updated at the same time). Reformats already-stored
-- values that match the old digit-only pattern exactly; anything that doesn't match
-- (garbage test data, already-blank values) is left untouched — it wasn't valid before
-- either and needs a human to fix it on next edit.

UPDATE drivers
SET phone = regexp_replace(phone, '^\+380(\d{2})(\d{3})(\d{2})(\d{2})$', '+38(0\1)-\2-\3-\4')
WHERE phone ~ '^\+380\d{9}$';

UPDATE clients
SET contact_person_phone = regexp_replace(contact_person_phone, '^\+380(\d{2})(\d{3})(\d{2})(\d{2})$', '+38(0\1)-\2-\3-\4')
WHERE contact_person_phone ~ '^\+380\d{9}$';

UPDATE clients
SET phone = regexp_replace(phone, '^\+380(\d{2})(\d{3})(\d{2})(\d{2})$', '+38(0\1)-\2-\3-\4')
WHERE phone ~ '^\+380\d{9}$';
