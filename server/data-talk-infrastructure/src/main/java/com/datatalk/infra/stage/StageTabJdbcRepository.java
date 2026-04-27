package com.datatalk.infra.stage;

import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.application.stage.StageTabConcurrencyException;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.datatalk.domain.stage.StageTabScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class StageTabJdbcRepository implements StageTabRepository {

    private final JdbcTemplate jdbc;

    public StageTabJdbcRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<StageTab> TAB_MAPPER = (rs, i) -> new StageTab(
        rs.getString("id"),
        rs.getString("type"),
        StageTabScope.fromWire(rs.getString("scope")),
        rs.getString("title"),
        rs.getString("connection_id"),
        rs.getString("database_name"),
        rs.getString("schema_name"),
        rs.getString("origin_session_id"),
        rs.getInt("payload_version"),
        rs.getInt("pinned") == 1,
        rs.getInt("archived") == 1,
        rs.getObject("archived_at") instanceof Number n ? n.longValue() : null,
        rs.getLong("created_at"),
        rs.getLong("last_touched_at")
    );

    private static final RowMapper<StageTabContent> CONTENT_MAPPER = (rs, i) -> new StageTabContent(
        rs.getString("tab_id"),
        rs.getString("payload_json"),
        rs.getString("content_text"),
        rs.getInt("content_version"),
        rs.getLong("updated_at")
    );

    @Override
    public int upsertMetadata(StageTab tab, Integer expectedPayloadVersion) {
        Optional<StageTab> existing = findById(tab.id());
        if (existing.isEmpty()) {
            if (expectedPayloadVersion != null && expectedPayloadVersion > 0) {
                throw new StageTabConcurrencyException(tab.id(), expectedPayloadVersion, 0);
            }
            return doInsert(tab);
        }

        int currentVersion = existing.get().payloadVersion();
        if (expectedPayloadVersion != null && currentVersion != expectedPayloadVersion) {
            throw new StageTabConcurrencyException(tab.id(), expectedPayloadVersion, currentVersion);
        }

        int newVersion = currentVersion + 1;
        jdbc.update("""
            UPDATE stage_tabs SET
                type = ?, scope = ?, title = ?, connection_id = ?, database_name = ?,
                schema_name = ?, origin_session_id = ?, payload_version = ?, pinned = ?,
                archived = ?, archived_at = ?, last_touched_at = ?
            WHERE id = ?
            """,
            tab.type(), tab.scope().wire(), tab.title(),
            tab.connectionId(), tab.databaseName(), tab.schemaName(),
            tab.originSessionId(), newVersion, tab.pinned() ? 1 : 0,
            tab.archived() ? 1 : 0, tab.archivedAt(),
            tab.lastTouchedAt(), tab.id());
        return newVersion;
    }

    private int doInsert(StageTab tab) {
        jdbc.update("""
            INSERT INTO stage_tabs(id, type, scope, title, connection_id, database_name,
                schema_name, origin_session_id, payload_version, pinned,
                archived, archived_at, created_at, last_touched_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            tab.id(), tab.type(), tab.scope().wire(), tab.title(),
            tab.connectionId(), tab.databaseName(), tab.schemaName(),
            tab.originSessionId(), 1,
            tab.pinned() ? 1 : 0, tab.archived() ? 1 : 0, tab.archivedAt(),
            tab.createdAt(), tab.lastTouchedAt());
        return 1;
    }

    @Override
    @Transactional
    public int upsertPayload(String tabId, String payloadJson, String contentText,
                             Integer expectedVersion, long updatedAt) {
        StageTab tab = findById(tabId)
            .orElseThrow(() -> new StageTabConcurrencyException(tabId, expectedVersion == null ? 0 : expectedVersion, 0));
        int currentVersion = tab.payloadVersion();
        if (expectedVersion != null && currentVersion != expectedVersion) {
            throw new StageTabConcurrencyException(tabId, expectedVersion, currentVersion);
        }

        boolean payloadExists = findContent(tabId).isPresent();
        int newVersion = payloadExists ? currentVersion + 1 : currentVersion;
        if (payloadExists) {
            jdbc.update("""
                UPDATE stage_tab_payload
                SET payload_json = ?, content_text = ?, content_version = ?, updated_at = ?
                WHERE tab_id = ?
                """, payloadJson, contentText, newVersion, updatedAt, tabId);
            jdbc.update("""
                UPDATE stage_tabs SET payload_version = ?, last_touched_at = ? WHERE id = ?
                """, newVersion, updatedAt, tabId);
        } else {
            jdbc.update("""
                INSERT INTO stage_tab_payload(tab_id, payload_json, content_text, content_version, updated_at)
                VALUES(?, ?, ?, ?, ?)
                """, tabId, payloadJson, contentText, newVersion, updatedAt);
        }
        return newVersion;
    }

    @Override
    public Optional<StageTab> findById(String id) {
        var list = jdbc.query("SELECT * FROM stage_tabs WHERE id = ?", TAB_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<StageTabContent> findContent(String tabId) {
        var list = jdbc.query(
            "SELECT tab_id, payload_json, content_text, content_version, updated_at" +
            " FROM stage_tab_payload WHERE tab_id = ?", CONTENT_MAPPER, tabId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<StageTab> list(ListFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM stage_tabs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (filter.scope() != null) {
            sql.append(" AND scope = ?");
            params.add(filter.scope().wire());
        }
        if (filter.type() != null) {
            sql.append(" AND type = ?");
            params.add(filter.type());
        }
        if (filter.connectionId() != null) {
            sql.append(" AND connection_id = ?");
            params.add(filter.connectionId());
        }
        if (filter.originSessionId() != null) {
            sql.append(" AND origin_session_id = ?");
            params.add(filter.originSessionId());
        }
        if (!filter.includeArchived()) {
            sql.append(" AND archived = 0");
        }
        if (filter.pinned() != null) {
            sql.append(" AND pinned = ?");
            params.add(filter.pinned() ? 1 : 0);
        }
        if (filter.lastTouchedAfter() != null) {
            sql.append(" AND last_touched_at > ?");
            params.add(filter.lastTouchedAfter());
        }
        if (filter.lastTouchedBefore() != null) {
            sql.append(" AND last_touched_at < ?");
            params.add(filter.lastTouchedBefore());
        }
        sql.append(" ORDER BY last_touched_at DESC");
        sql.append(" LIMIT ?");
        params.add(filter.limit());

        return jdbc.query(sql.toString(), TAB_MAPPER, params.toArray());
    }

    @Override
    public List<StageTab> recentByLastTouched(int limit) {
        return jdbc.query(
            "SELECT * FROM stage_tabs WHERE archived = 0 ORDER BY last_touched_at DESC LIMIT ?",
            TAB_MAPPER, limit);
    }

    @Override
    public boolean delete(String id) {
        return jdbc.update("DELETE FROM stage_tabs WHERE id = ?", id) > 0;
    }

    @Override
    public void setArchived(String id, boolean archived, Long archivedAt) {
        if (archived) {
            jdbc.update("UPDATE stage_tabs SET archived = 1, archived_at = ? WHERE id = ?",
                archivedAt, id);
        } else {
            jdbc.update("UPDATE stage_tabs SET archived = 0, archived_at = NULL WHERE id = ?", id);
        }
    }

    @Override
    public int archiveStaleSince(long thresholdEpochMillis) {
        return jdbc.update(
            "UPDATE stage_tabs SET archived = 1, archived_at = ?" +
            " WHERE archived = 0 AND last_touched_at < ?",
            thresholdEpochMillis, thresholdEpochMillis);
    }

    @Override
    public int countActive() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM stage_tabs WHERE archived = 0", Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public int countArchived() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM stage_tabs WHERE archived = 1", Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public List<StageTabContent> findContents(List<String> tabIds) {
        if (tabIds == null || tabIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", tabIds.stream().map(id -> "?").toList());
        return jdbc.query(
            "SELECT tab_id, payload_json, content_text, content_version, updated_at" +
            " FROM stage_tab_payload WHERE tab_id IN (" + placeholders + ")",
            CONTENT_MAPPER, tabIds.toArray());
    }
}
