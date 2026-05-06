-- SQL Server connection support: encrypt, trust server certificate, instance name
-- encrypt and trust flags use INTEGER for SQLite compatibility (0/1)
ALTER TABLE connections ADD COLUMN sqlserver_encrypt INTEGER DEFAULT 1;
ALTER TABLE connections ADD COLUMN sqlserver_trust_server_certificate INTEGER DEFAULT 1;
ALTER TABLE connections ADD COLUMN sqlserver_instance_name TEXT;
