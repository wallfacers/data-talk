# Error Recovery

How to handle common ingestion errors.

## SSRF Blocked

If you receive `INGESTION_SSRF_BLOCKED`:
- The URL host is in the deny list (private IPs, localhost, metadata endpoints)
- Ask user to provide a public HTTPS URL

## Auth Failed

If you receive `INGESTION_AUTH_FAILED`:
- Check if the credential exists in Settings → Credentials
- Verify the auth scheme (Bearer/API Key/Basic) matches the API requirement
- Re-create the credential if needed

## Payload Too Large

If you receive `INGESTION_PAYLOAD_TOO_LARGE` (>500MB):
- Suggest reducing `pageSize` in pagination config
- Suggest narrowing date/time range via query parameters
- Suggest reducing `maxPages`

## Token Expired

If you receive `INGESTION_TOKEN_INVALID` with reason `EXPIRED`:
- The 5-minute confirmation window has passed
- Ask user to re-open the ingestion Tab and click Confirm again
- A fresh token will be issued

## Dialect Unsupported

If you receive `INGESTION_DIALECT_UNSUPPORTED`:
- The target connection uses an unsupported database type
- Suggest switching to mysql/postgresql/h2/sqlite
- Inform user that per-dialect support is planned in follow-up releases
