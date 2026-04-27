package com.datatalk.application.stage;

/**
 * Thrown when a regex pattern provided to ui_find is invalid or exceeds safety limits.
 */
public class StageFindInvalidPatternException extends RuntimeException {

    private final String pattern;

    public StageFindInvalidPatternException(String pattern, Throwable cause) {
        super("invalid pattern: " + pattern, cause);
        this.pattern = pattern;
    }

    public StageFindInvalidPatternException(String pattern, String message) {
        super(message);
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }
}
