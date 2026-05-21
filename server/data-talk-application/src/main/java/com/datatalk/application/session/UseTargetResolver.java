package com.datatalk.application.session;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.persistence.ConnectionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class UseTargetResolver {

    private final SessionRepository sessions;
    private final ConnectionRepository connections;
    private final SessionDataContextRepository contexts;
    private final ConnectionTargetDiscoveryService discovery;
    private final Translator translator;
    private final Clock clock;

    public UseTargetResolver(
        SessionRepository sessions,
        ConnectionRepository connections,
        SessionDataContextRepository contexts,
        ConnectionTargetDiscoveryService discovery,
        Translator translator,
        Clock clock
    ) {
        this.sessions = sessions;
        this.connections = connections;
        this.contexts = contexts;
        this.discovery = discovery;
        this.translator = translator;
        this.clock = clock;
    }

    public ResolveUseResult resolve(String sessionId, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new IllegalArgumentException(translator.get("error.target.required"));
        }
        var session = sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException(translator.get("error.session.not_found", sessionId)));
        var current = contexts.findBySessionId(sessionId)
            .orElse(new SessionDataContextRecord(sessionId, session.connectionId(), null, null, null, null, session.updatedAt()));
        String target = rawTarget.trim();

        List<TargetOption> localMatches = new ArrayList<>();
        if (current.connectionId() != null && !current.connectionId().isBlank()) {
            var currentTargets = discovery.discover(current.connectionId());
            localMatches.addAll(matchCurrentConnectionTargets(current, currentTargets, target));
        }
        if (localMatches.size() == 1) {
            return ResolveUseResult.matched(localMatches.get(0));
        }
        if (localMatches.size() > 1) {
            return ResolveUseResult.ambiguous(localMatches);
        }

        List<TargetOption> connectionMatches = connections.findAll().stream()
            .filter(c -> equalsIgnoreCase(c.name(), target))
            .map(c -> new TargetOption(
                "connection",
                c.id(),
                c.name(),
                null,
                null,
                c.name(),
                new SessionDataContextRecord(
                    sessionId,
                    c.id(),
                    c.name(),
                    null,
                    null,
                    "connection",
                    clock.millis()
                )
            ))
            .toList();
        if (connectionMatches.size() == 1) {
            return ResolveUseResult.matched(connectionMatches.get(0));
        }
        if (connectionMatches.size() > 1) {
            return ResolveUseResult.ambiguous(connectionMatches);
        }

        List<TargetOption> suggestions = buildSuggestions(current, target);
        return ResolveUseResult.notFound(
            translator.get("error.use_target.not_found", target),
            suggestions
        );
    }

    private List<TargetOption> matchCurrentConnectionTargets(
        SessionDataContextRecord current,
        ConnectionTargetDiscoveryService.DiscoveryResult targets,
        String target
    ) {
        List<TargetOption> matches = new ArrayList<>();
        targets.databaseNames().stream()
            .filter(name -> equalsIgnoreCase(name, target))
            .findFirst()
            .ifPresent(name -> matches.add(new TargetOption(
                "database",
                targets.connectionId(),
                targets.connectionName(),
                name,
                null,
                name,
                new SessionDataContextRecord(
                    current.sessionId(),
                    targets.connectionId(),
                    targets.connectionName(),
                    name,
                    null,
                    "database",
                    clock.millis()
                )
            )));
        targets.schemaNames().stream()
            .filter(name -> equalsIgnoreCase(name, target))
            .findFirst()
            .ifPresent(name -> matches.add(new TargetOption(
                "schema",
                targets.connectionId(),
                targets.connectionName(),
                current.databaseName(),
                name,
                name,
                new SessionDataContextRecord(
                    current.sessionId(),
                    targets.connectionId(),
                    targets.connectionName(),
                    current.databaseName(),
                    name,
                    "schema",
                    clock.millis()
                )
            )));
        return matches;
    }

    private List<TargetOption> buildSuggestions(SessionDataContextRecord current, String target) {
        List<TargetOption> options = new ArrayList<>();
        if (current.connectionId() != null && !current.connectionId().isBlank()) {
            var currentTargets = discovery.discover(current.connectionId());
            currentTargets.databaseNames().stream()
                .filter(name -> containsIgnoreCase(name, target))
                .forEach(name -> options.add(new TargetOption("database", currentTargets.connectionId(),
                    currentTargets.connectionName(), name, null, name, null)));
            currentTargets.schemaNames().stream()
                .filter(name -> containsIgnoreCase(name, target))
                .forEach(name -> options.add(new TargetOption("schema", currentTargets.connectionId(),
                    currentTargets.connectionName(), current.databaseName(), name, name, null)));
        }
        connections.findAll().stream()
            .filter(c -> containsIgnoreCase(c.name(), target))
            .forEach(c -> options.add(new TargetOption("connection", c.id(), c.name(), null, null, c.name(), null)));

        return options.stream()
            .sorted(Comparator.comparing(TargetOption::label))
            .limit(5)
            .toList();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean containsIgnoreCase(String text, String query) {
        if (text == null || query == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    public record TargetOption(
        String level,
        String connectionId,
        String connectionName,
        String database,
        String schema,
        String label,
        SessionDataContextRecord resolvedContext
    ) {}

    public record ResolveUseResult(
        String status,
        SessionDataContextRecord context,
        TargetOption matchedTarget,
        List<TargetOption> candidates,
        List<TargetOption> suggestions,
        String message
    ) {
        public static ResolveUseResult matched(TargetOption target) {
            return new ResolveUseResult("matched", target.resolvedContext(), target, List.of(), List.of(), null);
        }

        public static ResolveUseResult ambiguous(List<TargetOption> candidates) {
            return new ResolveUseResult("ambiguous", null, null, candidates, List.of(), null);
        }

        public static ResolveUseResult notFound(String message, List<TargetOption> suggestions) {
            return new ResolveUseResult("not_found", null, null, List.of(), suggestions, message);
        }
    }
}
