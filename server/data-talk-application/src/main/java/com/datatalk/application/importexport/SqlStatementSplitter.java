package com.datatalk.application.importexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Minimal state machine that splits SQL text into complete statements.
 * Tracks quote pairing, line/block comments, and semicolon terminators.
 */
class SqlStatementSplitter {

    private static final Logger log = LoggerFactory.getLogger(SqlStatementSplitter.class);
    private static final int MAX_STATEMENT_BYTES = 10 * 1024 * 1024; // 10MB

    enum State {
        NORMAL, IN_SINGLE_QUOTE, IN_DOUBLE_QUOTE, IN_BACKTICK,
        IN_LINE_COMMENT, IN_BLOCK_COMMENT
    }

    /**
     * Reads the file and emits each complete SQL statement (terminated by {@code ;}) to the consumer.
     * Empty/blank statements are skipped. Statements exceeding 10MB are skipped with a warning.
     *
     * @param file     SQL file to read
     * @param consumer receives each complete, trimmed statement (without the trailing semicolon)
     */
    void split(Path file, Consumer<String> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State state = State.NORMAL;
            StringBuilder stmt = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                int len = line.length();
                for (int i = 0; i < len; i++) {
                    char c = line.charAt(i);
                    char next = (i + 1 < len) ? line.charAt(i + 1) : '\0';

                    switch (state) {
                        case NORMAL -> {
                            if (c == '-' && next == '-') {
                                state = State.IN_LINE_COMMENT;
                                i++; // consume second '-'
                            } else if (c == '/' && next == '*') {
                                state = State.IN_BLOCK_COMMENT;
                                i++; // consume '*'
                            } else if (c == '\'') {
                                state = State.IN_SINGLE_QUOTE;
                                stmt.append(c);
                            } else if (c == '"') {
                                state = State.IN_DOUBLE_QUOTE;
                                stmt.append(c);
                            } else if (c == '`') {
                                state = State.IN_BACKTICK;
                                stmt.append(c);
                            } else if (c == ';') {
                                String s = stmt.toString().trim();
                                if (!s.isBlank()) {
                                    if (stmt.length() > MAX_STATEMENT_BYTES) {
                                        log.warn("Skipping SQL statement exceeding {} bytes (got {})",
                                            MAX_STATEMENT_BYTES, stmt.length());
                                    } else {
                                        consumer.accept(s);
                                    }
                                }
                                stmt.setLength(0);
                            } else {
                                stmt.append(c);
                            }
                        }
                        case IN_SINGLE_QUOTE -> {
                            stmt.append(c);
                            if (c == '\'') {
                                if (next == '\'') {
                                    // escaped quote ''
                                    stmt.append(next);
                                    i++;
                                } else {
                                    state = State.NORMAL;
                                }
                            }
                        }
                        case IN_DOUBLE_QUOTE -> {
                            stmt.append(c);
                            if (c == '"') {
                                if (next == '"') {
                                    stmt.append(next);
                                    i++;
                                } else {
                                    state = State.NORMAL;
                                }
                            }
                        }
                        case IN_BACKTICK -> {
                            stmt.append(c);
                            if (c == '`') {
                                if (next == '`') {
                                    stmt.append(next);
                                    i++;
                                } else {
                                    state = State.NORMAL;
                                }
                            }
                        }
                        case IN_LINE_COMMENT -> {
                            // consume rest of line — just skip
                        }
                        case IN_BLOCK_COMMENT -> {
                            if (c == '*' && next == '/') {
                                state = State.NORMAL;
                                i++; // consume '/'
                            }
                        }
                    }
                }

                // Append newline between lines (preserves multi-line statement structure)
                if (state != State.IN_LINE_COMMENT) {
                    stmt.append('\n');
                }
                // Line comment ends at EOL
                if (state == State.IN_LINE_COMMENT) {
                    state = State.NORMAL;
                }
            }

            // Emit trailing statement without semicolon (if non-blank)
            String trailing = stmt.toString().trim();
            if (!trailing.isBlank()) {
                if (stmt.length() > MAX_STATEMENT_BYTES) {
                    log.warn("Skipping SQL statement exceeding {} bytes (got {})",
                        MAX_STATEMENT_BYTES, stmt.length());
                } else {
                    consumer.accept(trailing);
                }
            }
        }
    }
}
