package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErColumnMeta;
import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErTableMeta;

import java.util.List;

/**
 * Wire-format DTO for /api/er/seed-inspector. The frontend ER snapshot type
 * uses {@code fromColumn}/{@code toTable}/{@code toColumn} for FK relations
 * and {@code isPK}/{@code isFK}/{@code default} for column flags. Domain
 * records use the more verbose {@code source*}/{@code target*} and
 * {@code isPrimaryKey}/{@code isForeignKey}/{@code defaultValue} names, so
 * Jackson would serialize them with those names by default — silently
 * breaking the frontend, which then sees {@code undefined} for every FK
 * field and drops every relation in normalizeSeedRelations.
 *
 * The mapping below is the single source of truth for the wire contract.
 */
public record ErGraphResponse(
    List<TableNode> nodes,
    List<RelationEdge> edges,
    String summary,
    List<String> warnings
) {
    public static ErGraphResponse from(ErGraph graph) {
        return new ErGraphResponse(
            graph.nodes().stream().map(TableNode::from).toList(),
            graph.edges().stream().map(RelationEdge::from).toList(),
            graph.summary(),
            graph.warnings()
        );
    }

    public record TableNode(
        String name,
        String comment,
        List<ColumnMeta> columns,
        List<RelationEdge> fkOut
    ) {
        static TableNode from(ErTableMeta meta) {
            return new TableNode(
                meta.name(),
                meta.comment(),
                meta.columns().stream().map(ColumnMeta::from).toList(),
                meta.fkOut().stream().map(RelationEdge::from).toList()
            );
        }
    }

    public record ColumnMeta(
        String name,
        String type,
        boolean nullable,
        boolean isPK,
        boolean isFK,
        boolean isAutoIncrement,
        @com.fasterxml.jackson.annotation.JsonProperty("default") String defaultValue,
        String comment
    ) {
        static ColumnMeta from(ErColumnMeta col) {
            return new ColumnMeta(
                col.name(),
                col.type(),
                col.nullable(),
                col.isPrimaryKey(),
                col.isForeignKey(),
                col.isAutoIncrement(),
                col.defaultValue(),
                col.comment()
            );
        }
    }

    public record RelationEdge(
        String fromTable,
        String fromColumn,
        String toTable,
        String toColumn,
        String relationType,
        String source
    ) {
        static RelationEdge from(ErRelation r) {
            return new RelationEdge(
                r.sourceTable(),
                r.sourceColumn(),
                r.targetTable(),
                r.targetColumn(),
                r.relationType(),
                r.source()
            );
        }
    }
}
