package com.datatalk.infra.diagnostics;

import java.util.Optional;
import java.util.function.Function;

/**
 * Describes how to parse a text-based EXPLAIN plan into ExplainNode trees.
 * Each function operates on a single line of the raw plan text.
 *
 * - name        : grammar identifier (for error reporting)
 * - indentFn    : line -> indent level (0 = top); -1 = skip line (separator/header)
 * - operatorFn  : line -> operator name (or null = skip line)
 * - tableFn     : line -> table name (Optional)
 * - rowsFn      : line -> estimated rows (Optional)
 */
public record TextPlanGrammar(
    String name,
    Function<String, Integer> indentFn,
    Function<String, String> operatorFn,
    Function<String, Optional<String>> tableFn,
    Function<String, Optional<Long>> rowsFn
) {}