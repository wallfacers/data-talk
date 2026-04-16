# DataTalk Error Codes

Stable string ids used across Streamable HTTP `ErrorInfo.code`, tool responses, and UI i18n.
Append-only: never rename or repurpose. See `DataTalkErrorCodes.java`.

| code | retriable | trigger |
|---|---|---|
| schema.input_invalid | yes | action input fails JSON Schema |
| schema.output_invalid | no | handler output fails its output schema (handler bug) |
| connection.missing | no | execute_sql / layout_erd without active connection |
| connection.unreachable | yes | JDBC cannot reach target DB |
| sql.syntax_error | yes | target DB reports SQL syntax error |
| sql.timeout | no | JDBC statement exceeds 30s |
| sql.forbidden | no | non-SELECT (DDL/DML) -- MVP lock |
| artifact.supersedes_not_found | yes | supersedes points to unknown artifact |
| artifact.too_large | no | payload exceeds INLINE & HANDLE limits |
| action.timeout | no | handler exceeds timeoutMs |
| action.cancelled | no | abort or cascade cancel |
| client_action.unreachable | no | action.invoke pushed but client is gone |
| upstream.unavailable | yes | OpenCode unreachable |
| channel.resume_out_of_window | no | Last-Event-ID past ring buffer |
