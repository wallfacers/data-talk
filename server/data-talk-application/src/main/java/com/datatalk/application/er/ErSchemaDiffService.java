package com.datatalk.application.er;

import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.ErTableMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ErSchemaDiffService implements ErDdlGeneratorService.SchemaDiffPort {

    private final ErRelationDiscoveryService discovery;

    public ErSchemaDiffService(ErRelationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @Override
    public List<ErSchemaDiff> diff(ErDesignerPayload draft, String targetConnectionId) {
        if (targetConnectionId == null || targetConnectionId.isBlank()) {
            throw new IllegalArgumentException("Designer must be bound to a target connection before diff.");
        }

        List<String> draftTableNames = draft.tables().stream().map(ErDesignerTable::name).toList();
        ErGraph realGraph = discovery.discover(targetConnectionId, draftTableNames, 0);
        Map<String, ErTableMeta> realByName = realTables(realGraph);
        Set<String> realFkKeys = realFkKeys(realGraph);
        List<ErSchemaDiff> diffs = new ArrayList<>();

        for (ErDesignerTable draftTable : draft.tables()) {
            ErTableMeta realTable = realByName.get(draftTable.name());
            if (realTable == null) {
                diffs.add(new ErSchemaDiff.TableAdded(draftTable));
                addDesignerTableConstraints(diffs, draftTable);
                continue;
            }

            diffColumns(diffs, draftTable, realTable);
            addDesignerTableConstraints(diffs, draftTable);
        }

        for (String realName : realByName.keySet()) {
            boolean stillPresent = draft.tables().stream().anyMatch(t -> t.name().equals(realName));
            if (!stillPresent) diffs.add(new ErSchemaDiff.TableDropped(realName));
        }

        for (ErDesignerRelation relation : draft.relations()) {
            if (!"database_fk".equals(relation.constraintMethod())) continue;
            ErDesignerRelation resolved = resolveRelation(draft, relation);
            String key = fkKey(resolved.fromTableId(), resolved.fromColumnId(), resolved.toTableId(), resolved.toColumnId());
            if (!realFkKeys.contains(key)) diffs.add(new ErSchemaDiff.ConstraintAdded(resolved));
        }

        return diffs;
    }

    private static Map<String, ErTableMeta> realTables(ErGraph graph) {
        Map<String, ErTableMeta> tables = new HashMap<>();
        for (ErTableMeta table : graph.nodes()) tables.put(table.name(), table);
        return tables;
    }

    private static void diffColumns(List<ErSchemaDiff> diffs, ErDesignerTable draftTable, ErTableMeta realTable) {
        Map<String, ErColumnMeta> realColumns = new HashMap<>();
        for (ErColumnMeta column : realTable.columns()) realColumns.put(column.name(), column);

        for (var draftColumn : draftTable.columns()) {
            ErColumnMeta realColumn = realColumns.get(draftColumn.name());
            if (realColumn == null) {
                diffs.add(new ErSchemaDiff.ColumnAdded(draftTable.name(), draftColumn));
            } else if (!normalizeType(realColumn.type()).equals(normalizeType(draftColumn.type()))) {
                diffs.add(new ErSchemaDiff.ColumnTypeChanged(
                    draftTable.name(), draftColumn.name(), realColumn.type(), draftColumn.type()));
            }
        }

        for (ErColumnMeta realColumn : realTable.columns()) {
            boolean stillPresent = draftTable.columns().stream().anyMatch(c -> c.name().equals(realColumn.name()));
            if (!stillPresent) diffs.add(new ErSchemaDiff.ColumnDropped(draftTable.name(), realColumn.name()));
        }
    }

    private static void addDesignerTableConstraints(List<ErSchemaDiff> diffs, ErDesignerTable draftTable) {
        for (var index : draftTable.indexes()) diffs.add(new ErSchemaDiff.IndexAdded(draftTable.name(), index));
        for (var unique : draftTable.uniques()) {
            diffs.add(new ErSchemaDiff.ConstraintAdded(new ErSchemaDiff.UniqueConstraint(draftTable.name(), unique.columns())));
        }
    }

    private static Set<String> realFkKeys(ErGraph graph) {
        Set<String> keys = new HashSet<>();
        for (ErRelation edge : graph.edges()) {
            keys.add(fkKey(edge.sourceTable(), edge.sourceColumn(), edge.targetTable(), edge.targetColumn()));
        }
        for (ErTableMeta table : graph.nodes()) {
            for (ErRelation fk : table.fkOut()) {
                keys.add(fkKey(fk.sourceTable(), fk.sourceColumn(), fk.targetTable(), fk.targetColumn()));
                keys.add(fkKey(table.name(), fk.sourceColumn(), fk.targetTable(), fk.targetColumn()));
            }
        }
        return keys;
    }

    private static ErDesignerRelation resolveRelation(ErDesignerPayload draft, ErDesignerRelation relation) {
        String fromTable = tableName(draft, relation.fromTableId());
        String toTable = tableName(draft, relation.toTableId());
        String fromColumn = columnName(draft, relation.fromTableId(), relation.fromColumnId());
        String toColumn = columnName(draft, relation.toTableId(), relation.toColumnId());
        return new ErDesignerRelation(
            relation.id(), fromTable, fromColumn, toTable, toColumn, relation.type(), relation.constraintMethod());
    }

    private static String tableName(ErDesignerPayload draft, String tableId) {
        return draft.tables().stream()
            .filter(table -> table.id().equals(tableId))
            .map(ErDesignerTable::name)
            .findFirst()
            .orElse(tableId);
    }

    private static String columnName(ErDesignerPayload draft, String tableId, String columnId) {
        return draft.tables().stream()
            .filter(table -> table.id().equals(tableId))
            .flatMap(table -> table.columns().stream())
            .filter(column -> column.id().equals(columnId))
            .map(column -> column.name())
            .findFirst()
            .orElse(columnId);
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String fkKey(String fromTable, String fromColumn, String toTable, String toColumn) {
        return fromTable + "." + fromColumn + "->" + toTable + "." + toColumn;
    }
}
