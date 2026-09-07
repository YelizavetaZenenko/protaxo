ALTER TABLE clients ADD COLUMN last_name          VARCHAR(255);
ALTER TABLE clients ADD COLUMN first_name         VARCHAR(255);
ALTER TABLE clients ADD COLUMN middle_name        VARCHAR(255);
ALTER TABLE clients ADD COLUMN birth_date         DATE;
ALTER TABLE clients ADD COLUMN gender             VARCHAR(10);
ALTER TABLE clients ADD COLUMN employer_client_id BIGINT REFERENCES clients(id);
-- "position" is a SQL function name, so the column is job_title.
ALTER TABLE clients ADD COLUMN job_title          VARCHAR(255);
ALTER TABLE clients ADD COLUMN phone              VARCHAR(50);
ALTER TABLE clients ADD COLUMN email              VARCHAR(255);

CREATE INDEX idx_clients_employer_client_id ON clients (employer_client_id);
