-- Drivers are shown as "Працівники" of a client now; each can have a job title.
ALTER TABLE drivers ADD COLUMN position VARCHAR(255);
