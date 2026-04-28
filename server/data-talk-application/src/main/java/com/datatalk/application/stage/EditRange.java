package com.datatalk.application.stage;

/**
 * A 1-based line/column edit range; matches Monaco's IRange shape.
 */
public record EditRange(int startLine, int startColumn, int endLine, int endColumn) {

    public EditRange {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
        if (startColumn < 1) {
            throw new IllegalArgumentException("startColumn must be >= 1");
        }
        if (endLine < 1) {
            throw new IllegalArgumentException("endLine must be >= 1");
        }
        if (endColumn < 1) {
            throw new IllegalArgumentException("endColumn must be >= 1");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must be >= startLine");
        }
        if (startLine == endLine && endColumn < startColumn) {
            throw new IllegalArgumentException("endColumn must be >= startColumn when on the same line");
        }
    }
}
