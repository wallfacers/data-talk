package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.ConnectionContextRefreshService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.dto.ConnectionDto;
import com.datatalk.dto.SessionDataContextDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.update_connection_confirmable",
    executor = Executor.SERVER,
    description = "action.update_connection_confirmable.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L2 },
    category = { Category.MUTATION }
)
public class UpdateConnectionConfirmableAction implements ActionHandler<Map, Map> {

    private final ConnectionService connections;
    private final ConnectionContextRefreshService contextRefreshService;
    private final ConnectionRepository connectionRepo;
    private final SessionDataContextService sessionContexts;
    private final Translator translator;

    public UpdateConnectionConfirmableAction(
        ConnectionService connections,
        ConnectionContextRefreshService contextRefreshService,
        ConnectionRepository connectionRepo,
        SessionDataContextService sessionContexts,
        Translator translator
    ) {
        this.connections = connections;
        this.contextRefreshService = contextRefreshService;
        this.connectionRepo = connectionRepo;
        this.sessionContexts = sessionContexts;
        this.translator = translator;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.ofEntries(
            Map.entry("type", "object"),
            Map.entry("required", List.of("connectionId", "name", "kind")),
            Map.entry("properties", Map.ofEntries(
                Map.entry("connectionId", Map.of("type", "string")),
                Map.entry("name", Map.of("type", "string")),
                Map.entry("kind", Map.of("type", "string")),
                Map.entry("host", Map.of("type", "string")),
                Map.entry("port", Map.of("type", "integer")),
                Map.entry("databaseName", Map.of("type", "string")),
                Map.entry("username", Map.of("type", "string")),
                Map.entry("password", Map.of("type", "string")),
                Map.entry("connectTimeout", Map.of("type", "integer")),
                Map.entry("oracleServiceType", Map.of("type", "string")),
                Map.entry("confirm", Map.of("type", "boolean")),
                Map.entry("confirmationToken", Map.of("type", "string"))
            )),
            Map.entry("allOf", List.of(
                nonSqliteRequiresServerFieldsSchema(),
                confirmRequiresTokenSchema()
            ))
        );
    }

    private static Map<String, Object> nonSqliteRequiresServerFieldsSchema() {
        return Map.of(
            "if", Map.of(
                "properties", Map.of("kind", Map.of("const", ConnectionKind.SQLITE)),
                "required", List.of("kind")
            ),
            "else", Map.of("required", List.of("host", "port", "username"))
        );
    }

    private static Map<String, Object> confirmRequiresTokenSchema() {
        return Map.of(
            "if", Map.of(
                "properties", Map.of("confirm", Map.of("const", true)),
                "required", List.of("confirm")
            ),
            "then", Map.of("required", List.of("confirmationToken"))
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "confirm_required", Map.of("type", "boolean"),
                "confirmation_token", Map.of("type", "string"),
                "preview", Map.of("type", "object"),
                "ok", Map.of("type", "boolean"),
                "connection", Map.of("type", "object"),
                "data_context", Map.of("type", "object")
            )
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = String.valueOf(input.get("connectionId"));
        ConnectionRecord before = connectionRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException(translator.get("error.connection.unknown", connectionId)));
        var normalized = normalizeInput(input);

        String token = confirmationToken(ctx.sessionId(), before, normalized);
        boolean confirm = Boolean.TRUE.equals(input.get("confirm"));
        String providedToken = input.get("confirmationToken") == null ? null : String.valueOf(input.get("confirmationToken"));

        if (!confirm) {
            var out = new LinkedHashMap<String, Object>();
            out.put("confirm_required", true);
            out.put("confirmation_token", token);
            var preview = new LinkedHashMap<String, Object>();
            preview.put("connectionId", before.id());
            preview.put("before", toMap(before));
            preview.put("after", proposedUpdate(before, normalized));
            out.put("preview", preview);
            return CompletableFuture.completedFuture(out);
        }

        if (providedToken == null || !providedToken.equals(token)) {
            throw new IllegalArgumentException(translator.get("error.confirmation_token_required"));
        }

        connections.update(
            connectionId,
            normalized.name(),
            normalized.kind(),
            normalized.host(),
            normalized.port(),
            normalized.databaseName(),
            normalized.username(),
            normalized.password(),
            normalized.connectTimeout(),
            normalized.oracleServiceType()
        );
        contextRefreshService.refreshByConnectionId(connectionId);

        ConnectionDto connection = connections.get(connectionId);
        SessionDataContextDto dataContext = refreshCurrentSessionIfNeeded(ctx.sessionId(), connectionId);

        var out = new LinkedHashMap<String, Object>();
        out.put("ok", true);
        out.put("connection", toMap(connection));
        if (dataContext != null) {
            var ctxMap = new LinkedHashMap<String, Object>();
            ctxMap.put("sessionId", dataContext.sessionId());
            ctxMap.put("connectionId", dataContext.connectionId());
            ctxMap.put("connectionNameSnapshot", dataContext.connectionNameSnapshot());
            ctxMap.put("database", dataContext.database());
            ctxMap.put("schema", dataContext.schema());
            ctxMap.put("selectedLevel", dataContext.selectedLevel());
            ctxMap.put("updatedAt", dataContext.updatedAt());
            out.put("data_context", ctxMap);
        }
        return CompletableFuture.completedFuture(out);
    }

    private SessionDataContextDto refreshCurrentSessionIfNeeded(String sessionId, String connectionId) {
        var current = sessionContexts.get(sessionId);
        if (current.connectionId() == null || !current.connectionId().equals(connectionId)) {
            return null;
        }
        var refreshed = sessionContexts.validate(sessionId);
        return new SessionDataContextDto(
            refreshed.sessionId(),
            refreshed.connectionId(),
            refreshed.connectionNameSnapshot(),
            refreshed.databaseName(),
            refreshed.schemaName(),
            refreshed.selectedLevel(),
            refreshed.updatedAt()
        );
    }

    private Map<String, Object> proposedUpdate(ConnectionRecord current, NormalizedConnectionInput input) {
        var out = new LinkedHashMap<String, Object>();
        out.put("id", current.id());
        out.put("name", input.name());
        out.put("kind", input.kind());
        out.put("host", input.host());
        out.put("port", input.port());
        out.put("databaseName", input.databaseName());
        out.put("username", input.username());
        out.put("connectTimeout", input.connectTimeout());
        return out;
    }

    private static Map<String, Object> toMap(ConnectionDto c) {
        var out = new LinkedHashMap<String, Object>();
        out.put("id", c.id());
        out.put("name", c.name());
        out.put("kind", c.kind());
        out.put("host", c.host());
        out.put("port", c.port());
        out.put("databaseName", c.databaseName());
        out.put("username", c.username());
        out.put("createdAt", c.createdAt());
        out.put("connectTimeout", c.connectTimeout());
        out.put("lastTestStatus", c.lastTestStatus());
        out.put("lastTestAt", c.lastTestAt());
        return out;
    }

    private static Map<String, Object> toMap(ConnectionRecord c) {
        var out = new LinkedHashMap<String, Object>();
        out.put("id", c.id());
        out.put("name", c.name());
        out.put("kind", c.kind());
        out.put("host", c.host());
        out.put("port", c.port());
        out.put("databaseName", c.databaseName());
        out.put("username", c.username());
        out.put("createdAt", c.createdAt());
        out.put("connectTimeout", c.connectTimeout());
        out.put("lastTestStatus", c.lastTestStatus());
        out.put("lastTestAt", c.lastTestAt());
        out.put("schemaDigest", c.schemaDigest());
        return out;
    }

    private static String confirmationToken(String sessionId, ConnectionRecord before, NormalizedConnectionInput input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            digest(md, sessionId);
            digest(md, before.id());
            digest(md, before.name());
            digest(md, before.kind());
            digest(md, before.host());
            digest(md, String.valueOf(before.port()));
            digest(md, before.databaseName());
            digest(md, before.username());
            digest(md, Base64.getEncoder().encodeToString(before.passwordEnc()));
            digest(md, before.schemaDigest());
            digest(md, String.valueOf(before.connectTimeout()));
            digest(md, input.name());
            digest(md, input.kind());
            digest(md, input.host());
            digest(md, String.valueOf(input.port()));
            digest(md, input.databaseName());
            digest(md, input.username());
            digest(md, input.password());
            digest(md, String.valueOf(input.connectTimeout()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("cannot create confirmation token", e);
        }
    }

    private static NormalizedConnectionInput normalizeInput(Map input) {
        String kind = string(input, "kind");
        boolean sqlite = ConnectionKind.SQLITE.equalsIgnoreCase(kind);
        return new NormalizedConnectionInput(
            string(input, "name"),
            kind,
            sqlite ? "" : string(input, "host"),
            sqlite ? 0 : number(input, "port"),
            nullableString(input, "databaseName"),
            sqlite ? "" : string(input, "username"),
            nullableString(input, "password"),
            nullableInteger(input, "connectTimeout"),
            nullableString(input, "oracleServiceType")
        );
    }

    private static void digest(MessageDigest md, String value) {
        md.update((value == null ? "<null>" : value).getBytes(StandardCharsets.UTF_8));
        md.update((byte) 0);
    }

    private static String string(Map input, String key) {
        return String.valueOf(input.get(key));
    }

    private static String nullableString(Map input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Map input, String key) {
        Object value = input.get(key);
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static Integer nullableInteger(Map input, String key) {
        Object value = input.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private record NormalizedConnectionInput(
        String name,
        String kind,
        String host,
        int port,
        String databaseName,
        String username,
        String password,
        Integer connectTimeout,
        String oracleServiceType
    ) {}
}
