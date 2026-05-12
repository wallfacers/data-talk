package com.datatalk.application.ingestion.ddl;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.MappingColumn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class H2IngestionDdlAdapter implements IngestionDdlAdapter {

    @Override
    public boolean supports(String connectionKind) {
        return "h2".equalsIgnoreCase(connectionKind);
    }

    @Override
    public String generateCreateTable(String schema, String table, List<MappingColumn> columns) {
        List<MappingColumn> active = columns.stream().filter(c -> !c.skip()).toList();
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (schema != null && !schema.isBlank()) ddl.append('"').append(schema).append("\".");
        ddl.append('"').append(table).append("\" (\n");
        for (int i = 0; i < active.size(); i++) {
            MappingColumn col = active.get(i);
            ddl.append("  \"").append(col.targetName()).append("\" ").append(sqlTypeFor(col.type()));
            if (!col.nullable()) ddl.append(" NOT NULL");
            if (i < active.size() - 1) ddl.append(',');
            ddl.append('\n');
        }
        ddl.append(");");
        return ddl.toString();
    }

    @Override
    public String generateInsert(String schema, String table, List<MappingColumn> columns) {
        List<MappingColumn> active = columns.stream().filter(c -> !c.skip()).toList();
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        if (schema != null && !schema.isBlank()) sb.append('"').append(schema).append("\".");
        sb.append('"').append(table).append("\" (");
        sb.append(active.stream().map(c -> '"' + c.targetName() + '"').collect(Collectors.joining(", ")));
        sb.append(") VALUES (");
        sb.append(active.stream().map(c -> "?").collect(Collectors.joining(", ")));
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String sqlTypeFor(InferredType inferred) {
        return switch (inferred) {
            case BOOLEAN -> "BOOLEAN";
            case INTEGER_32 -> "INT";
            case INTEGER_64 -> "BIGINT";
            case DECIMAL -> "DECIMAL(38,10)";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case STRING_64 -> "VARCHAR(64)";
            case STRING_256 -> "VARCHAR(256)";
            case STRING_500 -> "VARCHAR(500)";
            case STRING_LONG -> "CLOB";
            case JSON -> "CLOB";
        };
    }
}
