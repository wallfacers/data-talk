-- Oracle connection support: service name vs SID mode
-- oracle_service_type: null or 'service' = service name mode; 'sid' = SID mode
ALTER TABLE connections ADD COLUMN oracle_service_type TEXT;
