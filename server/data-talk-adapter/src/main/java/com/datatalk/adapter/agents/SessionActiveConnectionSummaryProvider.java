package com.datatalk.adapter.agents;

import com.datatalk.application.connection.ActiveConnectionSummaryProvider;
import com.datatalk.application.history.SqlExecutionHistoryProvider;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.stage.ActiveSessionDirProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SessionActiveConnectionSummaryProvider implements ActiveConnectionSummaryProvider {

    private static final int RECENT_SUCCESS_LIMIT = 3;

    private final ActiveSessionDirProvider sessionDirProvider;
    private final SessionDataContextRepository contextRepo;
    private final ConnectionRepository connectionRepo;
    private final SqlExecutionHistoryProvider historyProvider;

    public SessionActiveConnectionSummaryProvider(
        ActiveSessionDirProvider sessionDirProvider,
        SessionDataContextRepository contextRepo,
        ConnectionRepository connectionRepo,
        SqlExecutionHistoryProvider historyProvider
    ) {
        this.sessionDirProvider = sessionDirProvider;
        this.contextRepo = contextRepo;
        this.connectionRepo = connectionRepo;
        this.historyProvider = historyProvider;
    }

    @Override
    public Optional<ConnectionSummary> summary() {
        Optional<String> sessionId = sessionDirProvider.currentSessionId();
        if (sessionId.isEmpty()) return Optional.empty();
        return contextRepo.findBySessionId(sessionId.get())
            .filter(ctx -> ctx.connectionId() != null && !ctx.connectionId().isBlank())
            .flatMap(ctx -> connectionRepo.findById(ctx.connectionId())
                .map(conn -> new ConnectionSummary(
                    conn.id(),
                    conn.kind(),
                    firstNonBlank(ctx.databaseName(), conn.databaseName()),
                    ctx.schemaName(),
                    recentQueries(sessionId.get())
                )));
    }

    private List<String> recentQueries(String sessionId) {
        return historyProvider.recentSuccesses(sessionId, RECENT_SUCCESS_LIMIT).stream()
            .map(SqlExecutionRecord::sqlText)
            .toList();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }
}
