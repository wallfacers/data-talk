package com.datatalk.application.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

/**
 * Dialect-aware SQL identifier quoter. Resolves a quote style from a connection
 * kind and emits the identifier with the correct delimiter and escape sequence.
 *
 * <p>Coverage: 19 first-class connection kinds (see
 * docs/DATA_SOURCE_TYPE_COMPATIBILITY.md "Identifier Quoting Per Dialect").
 * Unknown kinds fall back to ANSI double-quote with a WARN log.
 */
public final class IdentifierQuoter {

    private static final Logger log = LoggerFactory.getLogger(IdentifierQuoter.class);

    private static final Map<String, QuoteStyle> KIND_TO_STYLE = Map.ofEntries(
        Map.entry("mysql", QuoteStyle.BACKTICK),
        Map.entry("mariadb", QuoteStyle.BACKTICK),
        Map.entry("tidb", QuoteStyle.BACKTICK),
        Map.entry("oceanbase", QuoteStyle.BACKTICK),
        Map.entry("apache_doris", QuoteStyle.BACKTICK),
        Map.entry("starrocks", QuoteStyle.BACKTICK),
        Map.entry("clickhouse", QuoteStyle.BACKTICK),
        Map.entry("postgresql", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("postgres", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("h2", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("sqlite", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("oracle", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("duckdb", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("kingbase", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("dameng", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("gaussdb", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("hive", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("apache_hive", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("trino", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("presto", QuoteStyle.DOUBLE_QUOTE),
        Map.entry("sqlserver", QuoteStyle.BRACKET),
        Map.entry("mssql", QuoteStyle.BRACKET)
    );

    private IdentifierQuoter() {}

    public static String quote(String identifier, String connectionKind) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        QuoteStyle style = resolve(connectionKind);
        return switch (style) {
            case BACKTICK -> "`" + identifier.replace("`", "``") + "`";
            case DOUBLE_QUOTE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
            case BRACKET -> "[" + identifier.replace("]", "]]") + "]";
        };
    }

    public static QuoteStyle resolve(String connectionKind) {
        if (connectionKind == null || connectionKind.isBlank()) {
            log.warn("IdentifierQuoter fallback to DOUBLE_QUOTE: connectionKind is null/blank");
            return QuoteStyle.DOUBLE_QUOTE;
        }
        QuoteStyle style = KIND_TO_STYLE.get(connectionKind.toLowerCase(Locale.ROOT));
        if (style == null) {
            log.warn("IdentifierQuoter fallback to DOUBLE_QUOTE: unknown connectionKind='{}'", connectionKind);
            return QuoteStyle.DOUBLE_QUOTE;
        }
        return style;
    }
}
