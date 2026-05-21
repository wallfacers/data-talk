package com.datatalk.application.er;

import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErTableMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges a real-DB ErGraph into the existing designer draft so the frontend can
 * hydrate a fully-shaped ErDesignerPayload after a `sync_from_db` call.
 *
 * Semantics: the listed table names are pulled from the live DB and replace the
 * matching drafts (preserving stable table/column ids when names match). Draft
 * `database_fk` relations involving any synced table are rebuilt from the real
 * DB FKs; other relation kinds (`comment_ref`, virtual annotations) are kept and
 * their ids re-mapped to the merged tables.
 */
@Service
public class ErDesignerSyncService {

    private final ErRelationDiscoveryService discovery;

    public ErDesignerSyncService(ErRelationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    public ErDesignerPayload sync(ErDesignerPayload draft, String connectionId, List<String> tableFilter) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("sync_from_db requires bind_target first (connectionId is null).");
        }
        List<String> syncedNames = (tableFilter == null || tableFilter.isEmpty())
            ? draft.tables().stream().map(ErDesignerTable::name).toList()
            : tableFilter;
        if (syncedNames.isEmpty()) {
            throw new IllegalArgumentException(
                "Designer has no tables and no explicit `tables` list — nothing to sync.");
        }

        ErGraph graph = discovery.discover(connectionId, syncedNames, 0);
        return merge(draft, graph, new HashSet<>(syncedNames));
    }

    static ErDesignerPayload merge(ErDesignerPayload draft, ErGraph graph, Set<String> syncedSet) {
        Map<String, ErTableMeta> realByName = new HashMap<>();
        for (ErTableMeta t : graph.nodes()) realByName.put(t.name(), t);

        // Build the merged table list, preserving draft order for non-synced tables
        // and replacing each draft slot in place when its name was synced. Real-DB
        // tables that did not exist in the draft are appended at the end so their
        // order matches the discovery response order.
        List<ErDesignerTable> nextTables = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (ErDesignerTable t : draft.tables()) {
            if (syncedSet.contains(t.name())) {
                ErTableMeta real = realByName.get(t.name());
                // Discovery throws TablesNotFoundException upstream when an explicit name
                // is missing, so a synced name here is always present in the graph.
                if (real != null) {
                    nextTables.add(toDesignerTable(real, t));
                    placed.add(t.name());
                }
            } else {
                nextTables.add(t);
                placed.add(t.name());
            }
        }
        for (ErTableMeta real : graph.nodes()) {
            if (placed.contains(real.name())) continue;
            if (!syncedSet.contains(real.name())) continue;
            nextTables.add(toDesignerTable(real, null));
            placed.add(real.name());
        }

        // Indexes for relation rebuilding.
        Map<String, ErDesignerTable> nextByName = new HashMap<>();
        for (ErDesignerTable t : nextTables) nextByName.put(t.name(), t);
        Map<String, String> tableIdByName = new HashMap<>();
        Map<String, Map<String, String>> columnIdByName = new HashMap<>();
        for (ErDesignerTable t : nextTables) {
            tableIdByName.put(t.name(), t.id());
            Map<String, String> cols = new HashMap<>();
            for (ErDesignerColumn c : t.columns()) cols.put(c.name(), c.id());
            columnIdByName.put(t.name(), cols);
        }

        // Index of draft tables/columns by id (so we can look up names from the inbound relation ids).
        Map<String, ErDesignerTable> draftById = new HashMap<>();
        for (ErDesignerTable t : draft.tables()) draftById.put(t.id(), t);

        List<ErDesignerRelation> nextRelations = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (ErDesignerRelation rel : draft.relations()) {
            ErDesignerTable fromTable = draftById.get(rel.fromTableId());
            ErDesignerTable toTable = draftById.get(rel.toTableId());
            if (fromTable == null || toTable == null) continue;
            String fromName = fromTable.name();
            String toName = toTable.name();
            boolean involvesSynced = syncedSet.contains(fromName) || syncedSet.contains(toName);
            if (involvesSynced && "database_fk".equals(rel.constraintMethod())) continue;

            String fromColName = columnNameById(fromTable, rel.fromColumnId());
            String toColName = columnNameById(toTable, rel.toColumnId());
            String newFromTableId = tableIdByName.get(fromName);
            String newToTableId = tableIdByName.get(toName);
            String newFromColumnId = newFromTableId == null
                ? null
                : columnIdByName.getOrDefault(fromName, Map.of()).get(fromColName);
            String newToColumnId = newToTableId == null
                ? null
                : columnIdByName.getOrDefault(toName, Map.of()).get(toColName);
            if (newFromTableId == null || newToTableId == null
                || newFromColumnId == null || newToColumnId == null) continue;
            String key = relationKey(newFromTableId, newFromColumnId, newToTableId, newToColumnId, rel.constraintMethod());
            if (!seenKeys.add(key)) continue;
            nextRelations.add(new ErDesignerRelation(
                rel.id(),
                newFromTableId,
                newFromColumnId,
                newToTableId,
                newToColumnId,
                rel.type(),
                rel.constraintMethod()
            ));
        }

        for (ErTableMeta real : graph.nodes()) {
            if (!syncedSet.contains(real.name())) continue;
            for (ErRelation fk : real.fkOut()) {
                String fromName = real.name();
                String toName = fk.targetTable();
                if (!nextByName.containsKey(fromName) || !nextByName.containsKey(toName)) continue;
                String fromTableId = tableIdByName.get(fromName);
                String toTableId = tableIdByName.get(toName);
                String fromColumnId = columnIdByName.getOrDefault(fromName, Map.of()).get(fk.sourceColumn());
                String toColumnId = columnIdByName.getOrDefault(toName, Map.of()).get(fk.targetColumn());
                if (fromColumnId == null || toColumnId == null) continue;
                String key = relationKey(fromTableId, fromColumnId, toTableId, toColumnId, "database_fk");
                if (!seenKeys.add(key)) continue;
                String relType = fk.relationType() == null || fk.relationType().isBlank()
                    ? "many_to_one" : fk.relationType();
                // Include source AND target so two FKs from the same column to different
                // targets get distinct ids. (Rare in practice, but valid in MySQL.)
                String relId = "r_" + fromName + "_" + fk.sourceColumn()
                    + "__" + toName + "_" + fk.targetColumn();
                nextRelations.add(new ErDesignerRelation(
                    relId,
                    fromTableId,
                    fromColumnId,
                    toTableId,
                    toColumnId,
                    relType,
                    "database_fk"
                ));
            }
        }

        return new ErDesignerPayload(
            draft.dialect(),
            draft.targetConnectionId(),
            draft.targetDatabase(),
            draft.targetSchema(),
            nextTables,
            nextRelations
        );
    }

    private static ErDesignerTable toDesignerTable(ErTableMeta meta, ErDesignerTable existing) {
        String tableId = existing != null && existing.id() != null && !existing.id().isBlank()
            ? existing.id()
            : "t_" + meta.name();
        Map<String, String> existingColumnIds = new HashMap<>();
        if (existing != null) {
            for (ErDesignerColumn c : existing.columns()) existingColumnIds.put(c.name(), c.id());
        }

        List<ErDesignerColumn> columns = new ArrayList<>();
        for (ErColumnMeta col : meta.columns()) {
            String colId = existingColumnIds.containsKey(col.name())
                ? existingColumnIds.get(col.name())
                : "c_" + meta.name() + "_" + col.name();
            columns.add(new ErDesignerColumn(
                colId,
                col.name(),
                col.type(),
                col.nullable(),
                col.isPrimaryKey(),
                col.isAutoIncrement(),
                col.defaultValue(),
                col.comment()
            ));
        }
        return new ErDesignerTable(
            tableId,
            meta.name(),
            meta.comment(),
            columns,
            existing != null ? existing.indexes() : List.of(),
            existing != null ? existing.uniques() : List.of()
        );
    }

    private static String columnNameById(ErDesignerTable table, String columnId) {
        for (ErDesignerColumn c : table.columns()) {
            if (c.id() != null && c.id().equals(columnId)) return c.name();
        }
        return null;
    }

    private static String relationKey(
        String fromTableId, String fromColumnId, String toTableId, String toColumnId, String method
    ) {
        return fromTableId + "." + fromColumnId + "->" + toTableId + "." + toColumnId + "[" + method + "]";
    }
}
