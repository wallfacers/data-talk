-- Oracle connection support: service name vs SID mode
ALTER TABLE connections ADD COLUMN oracle_service_type TEXT;
-- nullable: null defaults to 'service' (service name mode)
-- 'service' = service name mode (jdbc:oracle:thin:@//host:port/service_name)
-- 'sid' = SID mode (jdbc:oracle:thin:@host:port:SID)
