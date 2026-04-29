package com.datatalk.adapter.dto;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

public record GenerateDdlResponse(
    String ddl,
    List<?> statements,
    List<?> skipped
) {
    public static GenerateDdlResponse from(Object result) {
        String ddl = stringValue(invoke(result, "ddl"));
        List<?> statements = listValue(invoke(result, "statements"));
        List<?> skipped = listValue(invoke(result, "skipped"));
        if (ddl == null || ddl.isBlank()) {
            ddl = joinStatements(statements);
        }
        return new GenerateDdlResponse(ddl, statements, skipped);
    }

    private static String joinStatements(List<?> statements) {
        if (statements == null || statements.isEmpty()) {
            return "";
        }
        return statements.stream()
            .map(statement -> stringValue(invoke(statement, "sql")))
            .filter(sql -> sql != null && !sql.isBlank())
            .map(sql -> sql.endsWith(";") ? sql : sql + ";")
            .collect(Collectors.joining("\n\n"));
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value instanceof String s ? s : null;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
