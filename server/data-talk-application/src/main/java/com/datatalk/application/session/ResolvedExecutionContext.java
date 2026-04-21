package com.datatalk.application.session;

import com.datatalk.application.persistence.ConnectionRecord;

public record ResolvedExecutionContext(
    ConnectionRecord connection,
    String database,
    String schema
) {}
